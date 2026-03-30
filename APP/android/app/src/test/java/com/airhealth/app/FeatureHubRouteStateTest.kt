package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureHubRouteStateTest {
    private fun activeRouteState(): FeatureHubRouteState {
        return FeatureHubRouteState(currentTimeMillis = { 1_000L }).apply {
            replaceEntitlementCacheState(
                EntitlementCacheState(
                    snapshot = CachedEntitlementSnapshot(
                        sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                        verifiedAtEpochMillis = 1_000L,
                    ),
                    isBackendReachable = true,
                    lastVerificationAttemptAtEpochMillis = 1_000L,
                ),
            )
        }
    }

    @Test
    fun openingFeatureSetsSelectedFeatureContext() {
        val routeState = FeatureHubRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)

        val route = routeState.route
        assertTrue(route is FeatureHubRoute.Feature)
        route as FeatureHubRoute.Feature
        assertEquals(FeatureKind.ORAL_HEALTH, route.context.feature)
        assertEquals("home", route.context.lastVisitedRouteId)
        assertEquals("feature_hub/oral_health", route.routeId)
    }

    @Test
    fun childRouteCanReturnToSelectedFeature() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.MEASURE)

        val actionRoute = routeState.route as FeatureHubRoute.Action
        assertEquals("feature_hub/fat_burning", actionRoute.context.lastVisitedRouteId)
        assertEquals("feature_hub/fat_burning/measure", actionRoute.routeId)

        routeState.returnToFeature()

        val featureRoute = routeState.route as FeatureHubRoute.Feature
        assertEquals(FeatureKind.FAT_BURNING, featureRoute.context.feature)
        assertEquals("feature_hub/fat_burning", featureRoute.routeId)
        assertNull(routeState.activeManagedAction())
    }

    @Test
    fun conflictingActionAttemptKeepsCurrentRouteAndReasonCode() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.openAction(FeatureAction.VIEW_HISTORY)

        val actionRoute = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.MEASURE, actionRoute.action)
        assertEquals(
            ActionLockReasonCode.CONFLICTING_ACTION_IN_PROGRESS,
            routeState.lastBlockedActionAttempt?.reasonCode,
        )
        assertEquals(ManagedAction.MEASURE, routeState.activeManagedAction())
    }

    @Test
    fun resolvingCurrentActionAllowsNextEntryPoint() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.returnToFeature()
        routeState.openAction(FeatureAction.SET_GOALS)

        val actionRoute = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.SET_GOALS, actionRoute.action)
        assertEquals(ManagedAction.SET_GOALS, routeState.activeManagedAction())
        assertNull(routeState.lastBlockedActionAttempt)
    }

    @Test
    fun activeEntitlementAllowsMeasurementFromFeatureHub() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 1_000L })
        routeState.replaceEntitlementCacheState(
            EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                    verifiedAtEpochMillis = 1_000L,
                ),
                isBackendReachable = true,
                lastVerificationAttemptAtEpochMillis = 1_000L,
            ),
        )

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)

        val route = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.MEASURE, route.action)
        assertNull(routeState.lastBlockedActionAttempt)
    }

    @Test
    fun temporaryAccessBlocksMeasurementButKeepsHistoryAvailable() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 2_100L })
        routeState.replaceEntitlementCacheState(
            EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.TRIAL_ACTIVE,
                    verifiedAtEpochMillis = 2_000L,
                ),
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 2_050L,
            ),
        )

        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.MEASURE)

        assertTrue(routeState.route is FeatureHubRoute.Feature)
        assertEquals(
            ActionLockReasonCode.TEMPORARY_ACCESS_RESTRICTION,
            routeState.lastBlockedActionAttempt?.reasonCode,
        )

        routeState.openAction(FeatureAction.VIEW_HISTORY)

        val route = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.VIEW_HISTORY, route.action)
    }

    @Test
    fun verificationFailureWithoutCacheLeavesOnlyReadOnlyRoutesAvailable() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 5_000L })
        routeState.replaceEntitlementCacheState(
            EntitlementCacheState(
                snapshot = null,
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 4_900L,
            ),
        )

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.GET_SUGGESTION)

        assertTrue(routeState.route is FeatureHubRoute.Feature)
        assertEquals(
            ActionLockReasonCode.READ_ONLY_MODE_RESTRICTION,
            routeState.lastBlockedActionAttempt?.reasonCode,
        )
        assertFalse(routeState.effectiveEntitlement.canRequestLiveSuggestions)

        routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)

        val route = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.CONSULT_PROFESSIONALS, route.action)
    }
}
