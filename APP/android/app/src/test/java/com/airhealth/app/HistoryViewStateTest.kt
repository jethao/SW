package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryViewStateTest {
    @Test
    fun oralHistoryTracksBaselineProgressAcrossFirstThreeSessions() {
        val store = SessionHistoryStoreState(
            records = listOf(
                oralRecord("oral-3", 3_000L, SessionSyncState.PENDING),
                oralRecord("oral-2", 2_000L, SessionSyncState.SYNCED),
                oralRecord("oral-1", 1_000L, SessionSyncState.SYNCED),
            ),
        )

        val surface = store.oralHistorySurface()

        assertEquals("Baseline ready", surface.baselineProgressLabel)
        assertEquals("Pending sync", surface.latestStatusLabel)
        assertEquals("Baseline session 3 of 3", surface.items.first().progressLabel)
        assertEquals("Baseline session 1 of 3", surface.items.last().progressLabel)
    }

    @Test
    fun oralHistoryShowsRemainingBaselineWorkWhenSessionsAreMissing() {
        val store = SessionHistoryStoreState(records = listOf(oralRecord("oral-1", 1_000L, SessionSyncState.PENDING)))

        val surface = store.oralHistorySurface()

        assertEquals("Baseline building 1/3", surface.baselineProgressLabel)
        assertTrue(surface.progressDetail.contains("2 more oral sessions"))
    }

    @Test
    fun fatHistoryBuildsFinalAndBestDeltaLabelsFromConsumerSafeRecords() {
        val store = SessionHistoryStoreState(
            records = listOf(
                fatRecord("fat-3", "fat-token-3", 3_000L, SessionSyncState.PENDING),
                fatRecord("fat-2", "fat-token-2", 2_000L, SessionSyncState.SYNCED),
                fatRecord("fat-1", "fat-token-1", 1_000L, SessionSyncState.SYNCED),
            ),
        )

        val surface = store.fatHistorySurface()

        assertEquals(1, surface.pendingCount)
        assertEquals(2, surface.syncedCount)
        assertTrue(surface.latestFinalDeltaLabel?.startsWith("Final delta +") == true)
        assertTrue(surface.bestDeltaLabel?.startsWith("Best delta +") == true)
        assertEquals(3, surface.items.size)
    }

    private fun oralRecord(
        sessionId: String,
        recordedAtEpochMillis: Long,
        syncState: SessionSyncState,
    ): PersistedSessionSummaryRecord {
        return PersistedSessionSummaryRecord(
            sessionId = sessionId,
            feature = FeatureKind.ORAL_HEALTH,
            resultToken = "$sessionId-token",
            recordedAtEpochMillis = recordedAtEpochMillis,
            syncState = syncState,
            summaryTitle = "Oral session complete",
            summaryDetail = "Result token $sessionId-token recorded for oral trend history.",
        )
    }

    private fun fatRecord(
        sessionId: String,
        resultToken: String,
        recordedAtEpochMillis: Long,
        syncState: SessionSyncState,
    ): PersistedSessionSummaryRecord {
        return PersistedSessionSummaryRecord(
            sessionId = sessionId,
            feature = FeatureKind.FAT_BURNING,
            resultToken = resultToken,
            recordedAtEpochMillis = recordedAtEpochMillis,
            syncState = syncState,
            summaryTitle = "Fat-burning session complete",
            summaryDetail = "Result token $resultToken recorded for fat-burning history.",
        )
    }
}
