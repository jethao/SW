import Foundation

private let maxSyncAttempts = 3

enum SessionSyncJobStatus: String {
    case pending = "pending"
    case retryScheduled = "retry_scheduled"
    case inFlight = "in_flight"
    case synced = "synced"
    case poisoned = "poisoned"
}

struct PersistedSessionSyncJob {
    let sessionID: String
    let feature: FeatureKind
    let resultToken: String
    let idempotencyKey: String
    let enqueueEpochMillis: Int64
    let attemptCount: Int
    let status: SessionSyncJobStatus
    let nextRetryAtEpochMillis: Int64?
    let lastFailureReason: String?

    var isEligibleForDispatch: Bool {
        status == .pending || status == .retryScheduled
    }

    func eligibleAt(nowEpochMillis: Int64) -> Bool {
        guard isEligibleForDispatch else {
            return false
        }

        return nextRetryAtEpochMillis == nil || nextRetryAtEpochMillis! <= nowEpochMillis
    }

    func encode() -> String {
        [
            sessionID,
            feature.rawValue,
            resultToken,
            idempotencyKey,
            String(enqueueEpochMillis),
            String(attemptCount),
            status.rawValue,
            nextRetryAtEpochMillis.map(String.init) ?? "",
            lastFailureReason ?? "",
        ].joined(separator: "|")
    }

    static func fromRecord(_ record: PersistedSessionSummaryRecord) -> PersistedSessionSyncJob {
        PersistedSessionSyncJob(
            sessionID: record.sessionID,
            feature: record.feature,
            resultToken: record.resultToken,
            idempotencyKey: "\(record.feature.rawValue):\(record.sessionID):\(record.resultToken)",
            enqueueEpochMillis: record.recordedAtEpochMillis,
            attemptCount: 0,
            status: record.syncState == .synced ? .synced : .pending,
            nextRetryAtEpochMillis: nil,
            lastFailureReason: nil
        )
    }

    static func decode(_ encoded: String) -> PersistedSessionSyncJob {
        let parts = encoded.components(separatedBy: "|")
        precondition(parts.count == 9, "Invalid persisted session sync job payload.")
        return PersistedSessionSyncJob(
            sessionID: parts[0],
            feature: FeatureKind(rawValue: parts[1]) ?? .oralHealth,
            resultToken: parts[2],
            idempotencyKey: parts[3],
            enqueueEpochMillis: Int64(parts[4]) ?? 0,
            attemptCount: Int(parts[5]) ?? 0,
            status: SessionSyncJobStatus(rawValue: parts[6]) ?? .pending,
            nextRetryAtEpochMillis: parts[7].isEmpty ? nil : Int64(parts[7]),
            lastFailureReason: parts[8].isEmpty ? nil : parts[8]
        )
    }
}

struct FeatureSyncQueueProjection {
    let feature: FeatureKind
    let pendingCount: Int
    let retryScheduledCount: Int
    let inFlightCount: Int
    let syncedCount: Int
    let poisonedCount: Int
    let nextEligibleJob: PersistedSessionSyncJob?
    let activeJob: PersistedSessionSyncJob?
}

struct SessionSyncDispatchResult {
    let queueState: SessionSyncQueueState
    let dispatchedJob: PersistedSessionSyncJob?
}

struct SessionSyncReconciliation {
    let historyStoreState: SessionHistoryStoreState
    let syncQueueState: SessionSyncQueueState
}

struct SessionSyncQueueState {
    let jobs: [PersistedSessionSyncJob]

    init(jobs: [PersistedSessionSyncJob] = []) {
        self.jobs = jobs.sorted { $0.enqueueEpochMillis < $1.enqueueEpochMillis }
    }

    func enqueue(_ record: PersistedSessionSummaryRecord) -> SessionSyncQueueState {
        guard !jobs.contains(where: { $0.sessionID == record.sessionID }) else {
            return self
        }

        return SessionSyncQueueState(jobs: jobs + [PersistedSessionSyncJob.fromRecord(record)])
    }

    func beginNextEligibleAttempt(nowEpochMillis: Int64) -> SessionSyncDispatchResult {
        guard let nextJob = jobs
            .filter({ $0.eligibleAt(nowEpochMillis: nowEpochMillis) })
            .min(by: { $0.enqueueEpochMillis < $1.enqueueEpochMillis }) else {
            return SessionSyncDispatchResult(queueState: self, dispatchedJob: nil)
        }

        let updatedJob = PersistedSessionSyncJob(
            sessionID: nextJob.sessionID,
            feature: nextJob.feature,
            resultToken: nextJob.resultToken,
            idempotencyKey: nextJob.idempotencyKey,
            enqueueEpochMillis: nextJob.enqueueEpochMillis,
            attemptCount: nextJob.attemptCount + 1,
            status: .inFlight,
            nextRetryAtEpochMillis: nil,
            lastFailureReason: nextJob.lastFailureReason
        )

        let nextState = SessionSyncQueueState(
            jobs: jobs.map { job in
                job.sessionID == updatedJob.sessionID ? updatedJob : job
            }
        )
        return SessionSyncDispatchResult(queueState: nextState, dispatchedJob: updatedJob)
    }

    func markAttemptSucceeded(sessionID: String) -> SessionSyncQueueState {
        SessionSyncQueueState(
            jobs: jobs.map { job in
                if job.sessionID == sessionID {
                    return PersistedSessionSyncJob(
                        sessionID: job.sessionID,
                        feature: job.feature,
                        resultToken: job.resultToken,
                        idempotencyKey: job.idempotencyKey,
                        enqueueEpochMillis: job.enqueueEpochMillis,
                        attemptCount: job.attemptCount,
                        status: .synced,
                        nextRetryAtEpochMillis: nil,
                        lastFailureReason: nil
                    )
                }
                return job
            }
        )
    }

    func markAttemptFailed(
        sessionID: String,
        nowEpochMillis: Int64,
        reasonCode: String
    ) -> SessionSyncQueueState {
        SessionSyncQueueState(
            jobs: jobs.map { job in
                guard job.sessionID == sessionID else {
                    return job
                }

                if job.attemptCount >= maxSyncAttempts {
                    return PersistedSessionSyncJob(
                        sessionID: job.sessionID,
                        feature: job.feature,
                        resultToken: job.resultToken,
                        idempotencyKey: job.idempotencyKey,
                        enqueueEpochMillis: job.enqueueEpochMillis,
                        attemptCount: job.attemptCount,
                        status: .poisoned,
                        nextRetryAtEpochMillis: nil,
                        lastFailureReason: reasonCode
                    )
                }

                return PersistedSessionSyncJob(
                    sessionID: job.sessionID,
                    feature: job.feature,
                    resultToken: job.resultToken,
                    idempotencyKey: job.idempotencyKey,
                    enqueueEpochMillis: job.enqueueEpochMillis,
                    attemptCount: job.attemptCount,
                    status: .retryScheduled,
                    nextRetryAtEpochMillis: nowEpochMillis + backoffMillis(for: job.attemptCount),
                    lastFailureReason: reasonCode
                )
            }
        )
    }

    func activeJob(for feature: FeatureKind) -> PersistedSessionSyncJob? {
        jobs.first { $0.feature == feature && $0.status == .inFlight }
    }

    func projection(for feature: FeatureKind, nowEpochMillis: Int64) -> FeatureSyncQueueProjection {
        let filtered = jobs
            .filter { $0.feature == feature }
            .sorted { $0.enqueueEpochMillis < $1.enqueueEpochMillis }

        return FeatureSyncQueueProjection(
            feature: feature,
            pendingCount: filtered.filter { $0.status == .pending }.count,
            retryScheduledCount: filtered.filter { $0.status == .retryScheduled }.count,
            inFlightCount: filtered.filter { $0.status == .inFlight }.count,
            syncedCount: filtered.filter { $0.status == .synced }.count,
            poisonedCount: filtered.filter { $0.status == .poisoned }.count,
            nextEligibleJob: filtered.first { $0.eligibleAt(nowEpochMillis: nowEpochMillis) },
            activeJob: filtered.first { $0.status == .inFlight }
        )
    }

    func encode() -> String {
        jobs.map { $0.encode() }.joined(separator: "\n")
    }

    static func decode(_ encoded: String) -> SessionSyncQueueState {
        guard !encoded.isEmpty else {
            return SessionSyncQueueState()
        }

        return SessionSyncQueueState(
            jobs: encoded
                .split(separator: "\n")
                .map(String.init)
                .filter { !$0.isEmpty }
                .map(PersistedSessionSyncJob.decode)
        )
    }
}

enum SessionSyncReconciler {
    static func recordCompletedSession(
        historyStoreState: SessionHistoryStoreState,
        syncQueueState: SessionSyncQueueState,
        session: MeasurementSessionState,
        recordedAtEpochMillis: Int64
    ) -> SessionSyncReconciliation {
        let existingRecord = historyStoreState.record(for: session.sessionID)
        let nextSyncState: SessionSyncState = existingRecord?.syncState == .synced ? .synced : .pending
        let record = PersistedSessionSummaryRecord.fromCompletedSession(
            session,
            recordedAtEpochMillis: recordedAtEpochMillis,
            syncState: nextSyncState
        )

        return SessionSyncReconciliation(
            historyStoreState: historyStoreState.upsert(record),
            syncQueueState: nextSyncState == .synced ? syncQueueState : syncQueueState.enqueue(record)
        )
    }

    static func markSessionSynced(
        historyStoreState: SessionHistoryStoreState,
        syncQueueState: SessionSyncQueueState,
        sessionID: String
    ) -> SessionSyncReconciliation {
        SessionSyncReconciliation(
            historyStoreState: historyStoreState.markSynced(sessionID: sessionID),
            syncQueueState: syncQueueState.markAttemptSucceeded(sessionID: sessionID)
        )
    }
}

private func backoffMillis(for attemptCount: Int) -> Int64 {
    switch attemptCount {
    case 1:
        return 60_000
    case 2:
        return 5 * 60_000
    default:
        return 15 * 60_000
    }
}
