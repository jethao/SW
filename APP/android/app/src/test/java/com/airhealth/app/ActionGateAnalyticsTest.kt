package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGateAnalyticsTest {
    @Test
    fun allowedTransitionEmitsNormalizedPayload() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 1_000L })
        routeState.replaceEntitlementCacheState(activeEntitlementState())

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)

        assertEquals(
            linkedMapOf(
                "feature" to "oral_health",
                "requested_action" to "measure",
                "outcome" to "allowed",
            ),
            routeState.actionGateAnalytics.events.last().payload(),
        )
    }

    @Test
    fun blockedTransitionEmitsReasonCodeWithoutInternalFields() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 1_000L })
        routeState.replaceEntitlementCacheState(activeEntitlementState())

        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.openAction(FeatureAction.VIEW_HISTORY)

        assertEquals(
            linkedMapOf(
                "feature" to "fat_burning",
                "requested_action" to "view_history",
                "outcome" to "blocked",
                "active_action" to "measure",
                "reason_code" to "conflicting_action_in_progress",
            ),
            routeState.actionGateAnalytics.events.last().payload(),
        )
    }

    @Test
    fun temporaryAccessBlocksMeasurementWithoutChangingRoute() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 1_000L })

        routeState.replaceEntitlementCacheState(
            EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                    verifiedAtEpochMillis = 1_000L,
                ),
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 2_000L,
            ),
        )
        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)

        assertTrue(routeState.route is FeatureHubRoute.Feature)
        assertEquals(
            ActionLockReasonCode.TEMPORARY_ACCESS_RESTRICTION,
            routeState.lastBlockedActionAttempt?.reasonCode,
        )
        assertEquals(
            linkedMapOf(
                "feature" to "oral_health",
                "requested_action" to "measure",
                "outcome" to "blocked",
                "reason_code" to "temporary_access_restriction",
            ),
            routeState.actionGateAnalytics.events.last().payload(),
        )
    }

    @Test
    fun readOnlyBlocksGoalEditButAllowsConsultProfessionals() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 100L + (24L * 60L * 60L * 1000L) + 5L })

        routeState.replaceEntitlementCacheState(
            EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.TRIAL_ACTIVE,
                    verifiedAtEpochMillis = 100L,
                ),
                isBackendReachable = true,
                lastVerificationAttemptAtEpochMillis = 100L,
            ),
        )
        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.SET_GOALS)

        assertEquals(
            ActionLockReasonCode.READ_ONLY_MODE_RESTRICTION,
            routeState.lastBlockedActionAttempt?.reasonCode,
        )
        assertEquals(
            linkedMapOf(
                "feature" to "fat_burning",
                "requested_action" to "set_goals",
                "outcome" to "blocked",
                "reason_code" to "read_only_mode_restriction",
            ),
            routeState.actionGateAnalytics.events.last().payload(),
        )

        routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)

        assertTrue(routeState.route is FeatureHubRoute.Action)
        assertEquals(
            linkedMapOf(
                "feature" to "fat_burning",
                "requested_action" to "consult_professionals",
                "outcome" to "allowed",
            ),
            routeState.actionGateAnalytics.events.last().payload(),
        )
    }

    @Test
    fun analyticsPayloadsStayRedactedForConsumerChannels() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 1_000L })
        routeState.replaceEntitlementCacheState(activeEntitlementState())

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)

        val payload = routeState.actionGateAnalytics.events.last().payload()

        assertTrue("session_id" !in payload.keys)
        assertTrue("result_token" !in payload.keys)
        assertTrue("hardware_id" !in payload.keys)
        assertTrue("factory_mode" !in payload.keys)
        assertTrue("manufacturing_log" !in payload.keys)
        assertTrue("detected_voc_type" !in payload.keys)
    }

    @Test
    fun consultConflictAnalyticsStayExplicitAndConsumerSafe() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 1_000L })
        routeState.replaceEntitlementCacheState(activeEntitlementState())

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)

        assertEquals(
            linkedMapOf(
                "feature" to "oral_health",
                "requested_action" to "consult_professionals",
                "outcome" to "blocked",
                "active_action" to "measure",
                "reason_code" to "conflicting_action_in_progress",
            ),
            routeState.actionGateAnalytics.events.last().payload(),
        )
    }

    @Test
    fun consultHandoffAnalyticsDoNotExposeMeasurementOrAccountFields() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 1_000L })
        routeState.replaceEntitlementCacheState(activeEntitlementState())

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)
        routeState.refreshConsultDirectory(FeatureKind.ORAL_HEALTH)
        routeState.beginConsultHandoff(
            feature = FeatureKind.ORAL_HEALTH,
            resourceTitle = "AirHealth Oral Wellness Coach",
        )
        routeState.confirmConsultHandoff()

        val handoffEvent = routeState.consultHandoffAnalytics.events.last()
        assertEquals("oral_health", handoffEvent.feature)
        assertEquals("AirHealth Oral Wellness Coach", handoffEvent.resourceTitle)
        assertEquals("care.airhealth.app", handoffEvent.targetHost)
        assertEquals("Open virtual consult", handoffEvent.launchLabel)
    }

    private fun activeEntitlementState(): EntitlementCacheState {
        return EntitlementCacheState(
            snapshot = CachedEntitlementSnapshot(
                sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                verifiedAtEpochMillis = 1_000L,
            ),
            isBackendReachable = true,
            lastVerificationAttemptAtEpochMillis = 1_000L,
        )
    }
}
