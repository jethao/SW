package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSyncQueueStateTest {
    @Test
    fun recordingCompletedSessionEnqueuesOneDurableJob() {
        val completedSession = completedSession(
            sessionId = "oral-session-001",
            feature = FeatureKind.ORAL_HEALTH,
            resultToken = "oral-token-001",
        )

        val first = SessionSyncReconciler.recordCompletedSession(
            historyStoreState = SessionHistoryStoreState(),
            syncQueueState = SessionSyncQueueState(),
            session = completedSession,
            recordedAtEpochMillis = 5_000L,
        )
        val second = SessionSyncReconciler.recordCompletedSession(
            historyStoreState = first.historyStoreState,
            syncQueueState = first.syncQueueState,
            session = completedSession,
            recordedAtEpochMillis = 6_000L,
        )

        assertEquals(1, second.historyStoreState.records.size)
        assertEquals(1, second.syncQueueState.jobs.size)

        val queueProjection = second.syncQueueState.projectionFor(FeatureKind.ORAL_HEALTH, 6_000L)
        assertEquals(1, queueProjection.pendingCount)
        assertEquals("oral_health:oral-session-001:oral-token-001", second.syncQueueState.jobs.single().idempotencyKey)
        assertEquals(
            second.syncQueueState,
            SessionSyncQueueState.decode(second.syncQueueState.encode()),
        )
    }

    @Test
    fun syncedRecordDoesNotRegressBackToPendingOnDuplicateCompletion() {
        val completedSession = completedSession(
            sessionId = "fat-session-001",
            feature = FeatureKind.FAT_BURNING,
            resultToken = "fat-token-001",
        )

        val initial = SessionSyncReconciler.recordCompletedSession(
            historyStoreState = SessionHistoryStoreState(),
            syncQueueState = SessionSyncQueueState(),
            session = completedSession,
            recordedAtEpochMillis = 9_000L,
        )
        val synced = SessionSyncReconciler.markSessionSynced(
            historyStoreState = initial.historyStoreState,
            syncQueueState = initial.syncQueueState,
            sessionId = "fat-session-001",
        )
        val duplicate = SessionSyncReconciler.recordCompletedSession(
            historyStoreState = synced.historyStoreState,
            syncQueueState = synced.syncQueueState,
            session = completedSession,
            recordedAtEpochMillis = 10_000L,
        )

        assertEquals(SessionSyncState.SYNCED, duplicate.historyStoreState.recordFor("fat-session-001")?.syncState)
        assertEquals(SessionSyncJobStatus.SYNCED, duplicate.syncQueueState.jobs.single().status)
    }

    @Test
    fun failingAttemptsSchedulesRetryAndEventuallyPoisonsTheJob() {
        val record = PersistedSessionSummaryRecord(
            sessionId = "oral-session-002",
            feature = FeatureKind.ORAL_HEALTH,
            resultToken = "oral-token-002",
            recordedAtEpochMillis = 1_000L,
            syncState = SessionSyncState.PENDING,
            summaryTitle = "Oral session complete",
            summaryDetail = "Result token oral-token-002 recorded for oral trend history.",
        )

        var queue = SessionSyncQueueState().enqueue(record)
        var dispatched = queue.beginNextEligibleAttempt(2_000L)
        queue = dispatched.queueState
        assertNotNull(dispatched.dispatchedJob)

        queue = queue.markAttemptFailed(
            sessionId = record.sessionId,
            nowEpochMillis = 2_000L,
            reasonCode = "network_unreachable",
        )
        var projection = queue.projectionFor(FeatureKind.ORAL_HEALTH, 2_001L)
        assertEquals(1, projection.retryScheduledCount)
        assertNull(projection.nextEligibleJob)

        dispatched = queue.beginNextEligibleAttempt(62_000L)
        queue = dispatched.queueState.markAttemptFailed(
            sessionId = record.sessionId,
            nowEpochMillis = 62_000L,
            reasonCode = "server_busy",
        )
        projection = queue.projectionFor(FeatureKind.ORAL_HEALTH, 63_000L)
        assertEquals(1, projection.retryScheduledCount)

        dispatched = queue.beginNextEligibleAttempt(363_000L)
        queue = dispatched.queueState.markAttemptFailed(
            sessionId = record.sessionId,
            nowEpochMillis = 363_000L,
            reasonCode = "unprocessable_payload",
        )
        projection = queue.projectionFor(FeatureKind.ORAL_HEALTH, 363_001L)
        assertEquals(1, projection.poisonedCount)
        assertTrue(queue.jobs.single().lastFailureReason?.contains("unprocessable") == true)
    }

    private fun completedSession(
        sessionId: String,
        feature: FeatureKind,
        resultToken: String,
    ): MeasurementSessionState {
        return MeasurementSessionCoordinator.reduce(
            MeasurementSessionCoordinator.reduce(
                MeasurementSessionCoordinator.reduce(
                    MeasurementSessionState.begin(
                        sessionId = sessionId,
                        feature = feature,
                    ),
                    MeasurementBleEvent.MeasurementStarted,
                ),
                MeasurementBleEvent.TerminalReadingAvailable(
                    MeasurementTerminalSummary(resultToken = resultToken),
                ),
            ),
            MeasurementBleEvent.TerminalReadingConfirmed,
        )
    }
}
