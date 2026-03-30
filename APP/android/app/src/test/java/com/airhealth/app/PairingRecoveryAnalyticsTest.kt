package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingRecoveryAnalyticsTest {
    @Test
    fun pairingFailureAndRecoveryEventsAreConsumerSafe() {
        val routeState = FeatureHubRouteState()

        routeState.openSetup()
        routeState.grantBluetoothPermission()
        routeState.discoverDevice()
        routeState.connectDiscoveredDevice()

        routeState.markDeviceIncompatible()
        assertEquals(
            linkedMapOf(
                "failure_step" to "incompatible",
                "recovery_action" to "surfaced",
            ),
            routeState.pairingRecoveryAnalytics.events.last().payload(),
        )

        routeState.restartDiscovery()
        assertEquals(
            linkedMapOf(
                "failure_step" to "incompatible",
                "recovery_action" to "retry",
            ),
            routeState.pairingRecoveryAnalytics.events.last().payload(),
        )

        routeState.discoverDevice()
        routeState.connectDiscoveredDevice()
        routeState.markDeviceNotReady()
        assertEquals(
            linkedMapOf(
                "failure_step" to "not_ready",
                "recovery_action" to "surfaced",
            ),
            routeState.pairingRecoveryAnalytics.events.last().payload(),
        )

        routeState.retryDeviceReadinessCheck()
        assertEquals(
            linkedMapOf(
                "failure_step" to "not_ready",
                "recovery_action" to "retry",
            ),
            routeState.pairingRecoveryAnalytics.events.last().payload(),
        )

        routeState.markDeviceNotReady()
        routeState.exitSetup()
        assertEquals(
            linkedMapOf(
                "failure_step" to "not_ready",
                "recovery_action" to "exit",
            ),
            routeState.pairingRecoveryAnalytics.events.last().payload(),
        )
    }
}
