package com.airhealth.app

import org.junit.Assert.assertEquals
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
}
