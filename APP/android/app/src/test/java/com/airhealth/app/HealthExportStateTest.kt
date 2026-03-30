package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthExportStateTest {
    @Test
    fun exportPayloadUsesConsumerSafeSummaryFieldsOnly() {
        val record = PersistedSessionSummaryRecord(
            sessionId = "oral-session-001",
            feature = FeatureKind.ORAL_HEALTH,
            resultToken = "oral-token-001",
            recordedAtEpochMillis = 10_000L,
            syncState = SessionSyncState.SYNCED,
            summaryTitle = "Oral session complete",
            summaryDetail = "Result token oral-token-001 recorded for oral trend history.",
        )

        val payload = HealthExportAdapter.payloadFor(
            record = record,
            platform = HealthExportPlatform.HEALTH_CONNECT,
        )

        assertEquals(
            setOf(
                "platform",
                "session_id",
                "feature",
                "result_token",
                "recorded_at_epoch_millis",
                "summary_title",
                "summary_detail",
            ),
            payload.asWireMap().keys,
        )
        assertEquals("health_connect", payload.asWireMap()["platform"])
        assertNull(payload.asWireMap()["sync_state"])
    }

    @Test
    fun exportingLatestSummaryRecordsSuccessAuditTrail() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 20_000L })

        routeState.recordDemoCompletedSession(FeatureKind.ORAL_HEALTH)
        routeState.setExportPermission(
            platform = HealthExportPlatform.HEALTH_CONNECT,
            permissionState = HealthExportPermissionState.GRANTED,
        )
        val payload = routeState.exportLatestCompletedSummary(
            feature = FeatureKind.ORAL_HEALTH,
            platform = HealthExportPlatform.HEALTH_CONNECT,
        )

        assertTrue(payload != null)
        val surface = routeState.exportAuditSurfaceFor(
            feature = FeatureKind.ORAL_HEALTH,
            platform = HealthExportPlatform.HEALTH_CONNECT,
        )
        assertEquals("Exported", surface.latestStatusLabel)
        assertEquals("Health Connect", surface.latestPlatformTitle)
        assertTrue(surface.records.single().exportedResultToken.contains("result"))
    }

    @Test
    fun exportFailureRecordsAuditWithoutMutatingHistoryStore() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 30_000L })

        routeState.recordDemoCompletedSession(FeatureKind.FAT_BURNING)
        val beforeCount = routeState.sessionHistoryStoreState.records.size

        routeState.failLatestCompletedSummaryExport(
            feature = FeatureKind.FAT_BURNING,
            platform = HealthExportPlatform.HEALTH_CONNECT,
            reasonCode = "health_connect_unavailable",
        )

        val surface = routeState.exportAuditSurfaceFor(
            feature = FeatureKind.FAT_BURNING,
            platform = HealthExportPlatform.HEALTH_CONNECT,
        )
        assertEquals("Export failed", surface.latestStatusLabel)
        assertEquals("health_connect_unavailable", surface.latestFailureReason)
        assertEquals(beforeCount, routeState.sessionHistoryStoreState.records.size)
    }

    @Test
    fun deniedPermissionCreatesExplicitRecoverableFailureAudit() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 40_000L })

        routeState.recordDemoCompletedSession(FeatureKind.ORAL_HEALTH)
        routeState.setExportPermission(
            platform = HealthExportPlatform.HEALTH_CONNECT,
            permissionState = HealthExportPermissionState.DENIED,
        )

        val payload = routeState.exportLatestCompletedSummary(
            feature = FeatureKind.ORAL_HEALTH,
            platform = HealthExportPlatform.HEALTH_CONNECT,
        )

        assertEquals(null, payload)
        val deniedSurface = routeState.exportAuditSurfaceFor(
            feature = FeatureKind.ORAL_HEALTH,
            platform = HealthExportPlatform.HEALTH_CONNECT,
        )
        assertEquals(HealthExportPermissionState.DENIED, deniedSurface.permissionState)
        assertEquals("permission_denied", deniedSurface.latestFailureReason)

        routeState.setExportPermission(
            platform = HealthExportPlatform.HEALTH_CONNECT,
            permissionState = HealthExportPermissionState.GRANTED,
        )
        val retriedPayload = routeState.exportLatestCompletedSummary(
            feature = FeatureKind.ORAL_HEALTH,
            platform = HealthExportPlatform.HEALTH_CONNECT,
        )

        assertTrue(retriedPayload != null)
    }
}
