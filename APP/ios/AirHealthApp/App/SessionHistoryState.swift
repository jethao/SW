import Foundation

enum SessionSyncState: String {
    case pending = "pending"
    case synced = "synced"
}

struct PersistedSessionSummaryRecord {
    let sessionID: String
    let feature: FeatureKind
    let resultToken: String
    let recordedAtEpochMillis: Int64
    let syncState: SessionSyncState
    let summaryTitle: String
    let summaryDetail: String

    func encode() -> String {
        [
            sessionID,
            feature.rawValue,
            resultToken,
            String(recordedAtEpochMillis),
            syncState.rawValue,
            summaryTitle,
            summaryDetail,
        ].joined(separator: "|")
    }

    static func decode(_ encoded: String) -> PersistedSessionSummaryRecord {
        let parts = encoded.components(separatedBy: "|")
        precondition(parts.count == 7, "Invalid persisted session summary payload.")
        return PersistedSessionSummaryRecord(
            sessionID: parts[0],
            feature: FeatureKind(rawValue: parts[1]) ?? .oralHealth,
            resultToken: parts[2],
            recordedAtEpochMillis: Int64(parts[3]) ?? 0,
            syncState: SessionSyncState(rawValue: parts[4]) ?? .pending,
            summaryTitle: parts[5],
            summaryDetail: parts[6]
        )
    }

    static func fromCompletedSession(
        _ session: MeasurementSessionState,
        recordedAtEpochMillis: Int64,
        syncState: SessionSyncState = .pending
    ) -> PersistedSessionSummaryRecord {
        precondition(session.phase == .complete, "Only completed sessions may be persisted into consumer history.")

        let title: String
        let detail: String
        switch session.feature {
        case .oralHealth:
            title = "Oral session complete"
            detail = "Result token \(session.terminalSummary?.resultToken ?? "pending") recorded for oral trend history."
        case .fatBurning:
            title = "Fat-burning session complete"
            detail = "Result token \(session.terminalSummary?.resultToken ?? "pending") recorded for fat-burning history."
        }

        return PersistedSessionSummaryRecord(
            sessionID: session.sessionID,
            feature: session.feature,
            resultToken: session.terminalSummary?.resultToken ?? "unknown",
            recordedAtEpochMillis: recordedAtEpochMillis,
            syncState: syncState,
            summaryTitle: title,
            summaryDetail: detail
        )
    }
}

struct HistoryProjectionItem {
    let sessionID: String
    let title: String
    let detail: String
    let statusLabel: String
    let recordedAtEpochMillis: Int64
}

struct FeatureHistoryProjection {
    let feature: FeatureKind
    let pendingCount: Int
    let syncedCount: Int
    let latestItem: HistoryProjectionItem?
    let items: [HistoryProjectionItem]
}

struct SessionHistoryStoreState {
    let records: [PersistedSessionSummaryRecord]

    init(records: [PersistedSessionSummaryRecord] = []) {
        self.records = records.sorted { $0.recordedAtEpochMillis > $1.recordedAtEpochMillis }
    }

    func upsert(_ record: PersistedSessionSummaryRecord) -> SessionHistoryStoreState {
        let remaining = records.filter { $0.sessionID != record.sessionID }
        return SessionHistoryStoreState(records: remaining + [record])
    }

    func markSynced(sessionID: String) -> SessionHistoryStoreState {
        SessionHistoryStoreState(
            records: records.map { record in
                if record.sessionID == sessionID {
                    return PersistedSessionSummaryRecord(
                        sessionID: record.sessionID,
                        feature: record.feature,
                        resultToken: record.resultToken,
                        recordedAtEpochMillis: record.recordedAtEpochMillis,
                        syncState: .synced,
                        summaryTitle: record.summaryTitle,
                        summaryDetail: record.summaryDetail
                    )
                }
                return record
            }
        )
    }

    func projection(for feature: FeatureKind) -> FeatureHistoryProjection {
        let filtered = records.filter { $0.feature == feature }.sorted { $0.recordedAtEpochMillis > $1.recordedAtEpochMillis }
        let items = filtered.map { record in
            HistoryProjectionItem(
                sessionID: record.sessionID,
                title: record.summaryTitle,
                detail: record.summaryDetail,
                statusLabel: record.syncState == .pending ? "Pending sync" : "Synced",
                recordedAtEpochMillis: record.recordedAtEpochMillis
            )
        }
        return FeatureHistoryProjection(
            feature: feature,
            pendingCount: filtered.filter { $0.syncState == .pending }.count,
            syncedCount: filtered.filter { $0.syncState == .synced }.count,
            latestItem: items.first,
            items: items
        )
    }

    func encode() -> String {
        records.map { $0.encode() }.joined(separator: "\n")
    }

    static func decode(_ encoded: String) -> SessionHistoryStoreState {
        guard !encoded.isEmpty else {
            return SessionHistoryStoreState()
        }
        return SessionHistoryStoreState(
            records: encoded
                .split(separator: "\n")
                .map(String.init)
                .filter { !$0.isEmpty }
                .map(PersistedSessionSummaryRecord.decode)
        )
    }
}
