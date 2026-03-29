package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureHubRouteStateTest {
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
        val routeState = FeatureHubRouteState()

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
        val routeState = FeatureHubRouteState()

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
        val routeState = FeatureHubRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.returnToFeature()
        routeState.openAction(FeatureAction.SET_GOALS)

        val actionRoute = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.SET_GOALS, actionRoute.action)
        assertEquals(ManagedAction.SET_GOALS, routeState.activeManagedAction())
        assertNull(routeState.lastBlockedActionAttempt)
    }
}
