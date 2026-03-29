package com.airhealth.app

import org.junit.Assert.assertEquals
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
    }
}
