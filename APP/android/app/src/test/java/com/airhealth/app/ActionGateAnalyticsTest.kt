package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGateAnalyticsTest {
    @Test
    fun allowedTransitionEmitsNormalizedPayload() {
        val routeState = FeatureHubRouteState()

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
        val routeState = FeatureHubRouteState()

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
}
