package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHistoryStateTest {
    @Test
    fun completedSummaryRoundTripsThroughPersistenceEncoding() {
        val completedSession = MeasurementSessionCoordinator.reduce(
            MeasurementSessionCoordinator.reduce(
                MeasurementSessionCoordinator.reduce(
                    MeasurementSessionState.begin(
                        sessionId = "oral-session-001",
                        feature = FeatureKind.ORAL_HEALTH,
                    ),
                    MeasurementBleEvent.MeasurementStarted,
                ),
                MeasurementBleEvent.TerminalReadingAvailable(
                    MeasurementTerminalSummary(resultToken = "oral-token-001"),
                ),
            ),
            MeasurementBleEvent.TerminalReadingConfirmed,
        )

        val store = SessionHistoryStoreState().upsert(
            PersistedSessionSummaryRecord.fromCompletedSession(
                session = completedSession,
                recordedAtEpochMillis = 5_000L,
            ),
        )

        val decoded = SessionHistoryStoreState.decode(store.encode())
        val projection = decoded.projectionFor(FeatureKind.ORAL_HEALTH)

        assertEquals(1, projection.pendingCount)
        assertEquals(0, projection.syncedCount)
        assertEquals("Oral session complete", projection.latestItem?.title)
        assertEquals(
            "Saved oral trend summary for later history review.",
            projection.latestItem?.detail,
        )
    }

    @Test
    fun projectionsSeparatePendingAndSyncedRecordsPerFeature() {
        val oralRecord = PersistedSessionSummaryRecord(
            sessionId = "oral-1",
            feature = FeatureKind.ORAL_HEALTH,
            resultToken = "oral-token",
            recordedAtEpochMillis = 3_000L,
            syncState = SessionSyncState.PENDING,
            summaryTitle = "Oral session complete",
            summaryDetail = "Token oral-token stored for oral trend history.",
        )
        val fatRecord = PersistedSessionSummaryRecord(
            sessionId = "fat-1",
            feature = FeatureKind.FAT_BURNING,
            resultToken = "fat-token",
            recordedAtEpochMillis = 4_000L,
            syncState = SessionSyncState.PENDING,
            summaryTitle = "Fat-burning session complete",
            summaryDetail = "Token fat-token stored for fat-burning history.",
        )

        val store = SessionHistoryStoreState()
            .upsert(oralRecord)
            .upsert(fatRecord)
            .markSynced("fat-1")

        val oralProjection = store.projectionFor(FeatureKind.ORAL_HEALTH)
        assertEquals(1, oralProjection.pendingCount)
        assertEquals(0, oralProjection.syncedCount)

        val fatProjection = store.projectionFor(FeatureKind.FAT_BURNING)
        assertEquals(0, fatProjection.pendingCount)
        assertEquals(1, fatProjection.syncedCount)
        assertEquals("Synced", fatProjection.latestItem?.statusLabel)
        assertNotNull(fatProjection.latestItem)
        assertEquals(
            "Saved fat-burning trend summary for later history review.",
            fatProjection.latestItem?.detail,
        )
    }
}
