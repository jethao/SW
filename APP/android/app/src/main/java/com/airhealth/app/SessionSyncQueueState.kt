package com.airhealth.app

private const val MAX_SYNC_ATTEMPTS = 3

enum class SessionSyncJobStatus(
    val wireValue: String,
) {
    PENDING("pending"),
    RETRY_SCHEDULED("retry_scheduled"),
    IN_FLIGHT("in_flight"),
    SYNCED("synced"),
    POISONED("poisoned"),
}

data class PersistedSessionSyncJob(
    val sessionId: String,
    val feature: FeatureKind,
    val resultToken: String,
    val idempotencyKey: String,
    val enqueueEpochMillis: Long,
    val attemptCount: Int,
    val status: SessionSyncJobStatus,
    val nextRetryAtEpochMillis: Long?,
    val lastFailureReason: String?,
) {
    val isEligibleForDispatch: Boolean
        get() = status == SessionSyncJobStatus.PENDING || status == SessionSyncJobStatus.RETRY_SCHEDULED

    fun eligibleAt(nowEpochMillis: Long): Boolean {
        if (!isEligibleForDispatch) {
            return false
        }

        return nextRetryAtEpochMillis == null || nextRetryAtEpochMillis <= nowEpochMillis
    }

    fun encode(): String {
        return listOf(
            sessionId,
            feature.routeId,
            resultToken,
            idempotencyKey,
            enqueueEpochMillis.toString(),
            attemptCount.toString(),
            status.wireValue,
            nextRetryAtEpochMillis?.toString().orEmpty(),
            lastFailureReason.orEmpty(),
        ).joinToString("|")
    }

    companion object {
        fun fromRecord(record: PersistedSessionSummaryRecord): PersistedSessionSyncJob {
            return PersistedSessionSyncJob(
                sessionId = record.sessionId,
                feature = record.feature,
                resultToken = record.resultToken,
                idempotencyKey = "${record.feature.routeId}:${record.sessionId}:${record.resultToken}",
                enqueueEpochMillis = record.recordedAtEpochMillis,
                attemptCount = 0,
                status = if (record.syncState == SessionSyncState.SYNCED) {
                    SessionSyncJobStatus.SYNCED
                } else {
                    SessionSyncJobStatus.PENDING
                },
                nextRetryAtEpochMillis = null,
                lastFailureReason = null,
            )
        }

        fun decode(encoded: String): PersistedSessionSyncJob {
            val parts = encoded.split("|")
            require(parts.size == 9) { "Invalid persisted session sync job payload." }
            return PersistedSessionSyncJob(
                sessionId = parts[0],
                feature = FeatureKind.entries.first { it.routeId == parts[1] },
                resultToken = parts[2],
                idempotencyKey = parts[3],
                enqueueEpochMillis = parts[4].toLong(),
                attemptCount = parts[5].toInt(),
                status = SessionSyncJobStatus.entries.first { it.wireValue == parts[6] },
                nextRetryAtEpochMillis = parts[7].ifBlank { null }?.toLong(),
                lastFailureReason = parts[8].ifBlank { null },
            )
        }
    }
}

data class FeatureSyncQueueProjection(
    val feature: FeatureKind,
    val pendingCount: Int,
    val retryScheduledCount: Int,
    val inFlightCount: Int,
    val syncedCount: Int,
    val poisonedCount: Int,
    val nextEligibleJob: PersistedSessionSyncJob?,
    val activeJob: PersistedSessionSyncJob?,
)

data class SessionSyncDispatchResult(
    val queueState: SessionSyncQueueState,
    val dispatchedJob: PersistedSessionSyncJob?,
)

data class SessionSyncReconciliation(
    val historyStoreState: SessionHistoryStoreState,
    val syncQueueState: SessionSyncQueueState,
)

data class SessionSyncQueueState(
    val jobs: List<PersistedSessionSyncJob> = emptyList(),
) {
    fun enqueue(record: PersistedSessionSummaryRecord): SessionSyncQueueState {
        if (jobs.any { it.sessionId == record.sessionId }) {
            return this
        }

        return copy(jobs = (jobs + PersistedSessionSyncJob.fromRecord(record)).sortedBy { it.enqueueEpochMillis })
    }

    fun beginNextEligibleAttempt(nowEpochMillis: Long): SessionSyncDispatchResult {
        val nextJob = jobs
            .filter { it.eligibleAt(nowEpochMillis) }
            .minByOrNull { it.enqueueEpochMillis }
            ?: return SessionSyncDispatchResult(this, null)

        val updatedJob = nextJob.copy(
            attemptCount = nextJob.attemptCount + 1,
            status = SessionSyncJobStatus.IN_FLIGHT,
            nextRetryAtEpochMillis = null,
        )

        return SessionSyncDispatchResult(
            queueState = copy(
                jobs = jobs.map { job ->
                    if (job.sessionId == updatedJob.sessionId) updatedJob else job
                }.sortedBy { it.enqueueEpochMillis },
            ),
            dispatchedJob = updatedJob,
        )
    }

    fun markAttemptSucceeded(sessionId: String): SessionSyncQueueState {
        return copy(
            jobs = jobs.map { job ->
                if (job.sessionId == sessionId) {
                    job.copy(
                        status = SessionSyncJobStatus.SYNCED,
                        nextRetryAtEpochMillis = null,
                        lastFailureReason = null,
                    )
                } else {
                    job
                }
            }.sortedBy { it.enqueueEpochMillis },
        )
    }

    fun markAttemptFailed(
        sessionId: String,
        nowEpochMillis: Long,
        reasonCode: String,
    ): SessionSyncQueueState {
        return copy(
            jobs = jobs.map { job ->
                if (job.sessionId != sessionId) {
                    job
                } else if (job.attemptCount >= MAX_SYNC_ATTEMPTS) {
                    job.copy(
                        status = SessionSyncJobStatus.POISONED,
                        nextRetryAtEpochMillis = null,
                        lastFailureReason = reasonCode,
                    )
                } else {
                    job.copy(
                        status = SessionSyncJobStatus.RETRY_SCHEDULED,
                        nextRetryAtEpochMillis = nowEpochMillis + backoffMillisFor(job.attemptCount),
                        lastFailureReason = reasonCode,
                    )
                }
            }.sortedBy { it.enqueueEpochMillis },
        )
    }

    fun activeJobFor(feature: FeatureKind): PersistedSessionSyncJob? {
        return jobs.firstOrNull { it.feature == feature && it.status == SessionSyncJobStatus.IN_FLIGHT }
    }

    fun projectionFor(feature: FeatureKind, nowEpochMillis: Long): FeatureSyncQueueProjection {
        val filtered = jobs.filter { it.feature == feature }.sortedBy { it.enqueueEpochMillis }
        return FeatureSyncQueueProjection(
            feature = feature,
            pendingCount = filtered.count { it.status == SessionSyncJobStatus.PENDING },
            retryScheduledCount = filtered.count { it.status == SessionSyncJobStatus.RETRY_SCHEDULED },
            inFlightCount = filtered.count { it.status == SessionSyncJobStatus.IN_FLIGHT },
            syncedCount = filtered.count { it.status == SessionSyncJobStatus.SYNCED },
            poisonedCount = filtered.count { it.status == SessionSyncJobStatus.POISONED },
            nextEligibleJob = filtered.firstOrNull { it.eligibleAt(nowEpochMillis) },
            activeJob = filtered.firstOrNull { it.status == SessionSyncJobStatus.IN_FLIGHT },
        )
    }

    fun encode(): String {
        return jobs.joinToString("\n") { it.encode() }
    }

    companion object {
        fun decode(encoded: String): SessionSyncQueueState {
            if (encoded.isBlank()) {
                return SessionSyncQueueState()
            }

            return SessionSyncQueueState(
                jobs = encoded
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .map(PersistedSessionSyncJob::decode)
                    .sortedBy { it.enqueueEpochMillis }
                    .toList(),
            )
        }
    }
}

object SessionSyncReconciler {
    fun recordCompletedSession(
        historyStoreState: SessionHistoryStoreState,
        syncQueueState: SessionSyncQueueState,
        session: MeasurementSessionState,
        recordedAtEpochMillis: Long,
    ): SessionSyncReconciliation {
        val existingRecord = historyStoreState.recordFor(session.sessionId)
        val nextSyncState = if (existingRecord?.syncState == SessionSyncState.SYNCED) {
            SessionSyncState.SYNCED
        } else {
            SessionSyncState.PENDING
        }
        val record = PersistedSessionSummaryRecord.fromCompletedSession(
            session = session,
            recordedAtEpochMillis = recordedAtEpochMillis,
            syncState = nextSyncState,
        )
        return SessionSyncReconciliation(
            historyStoreState = historyStoreState.upsert(record),
            syncQueueState = if (nextSyncState == SessionSyncState.SYNCED) {
                syncQueueState
            } else {
                syncQueueState.enqueue(record)
            },
        )
    }

    fun markSessionSynced(
        historyStoreState: SessionHistoryStoreState,
        syncQueueState: SessionSyncQueueState,
        sessionId: String,
    ): SessionSyncReconciliation {
        return SessionSyncReconciliation(
            historyStoreState = historyStoreState.markSynced(sessionId),
            syncQueueState = syncQueueState.markAttemptSucceeded(sessionId),
        )
    }
}

private fun backoffMillisFor(attemptCount: Int): Long {
    return when (attemptCount) {
        1 -> 60_000L
        2 -> 5 * 60_000L
        else -> 15 * 60_000L
    }
}
