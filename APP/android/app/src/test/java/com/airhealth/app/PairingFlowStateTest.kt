package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingFlowStateTest {
    @Test
    fun setupFlowCanMoveFromPermissionToConnectedDevice() {
        val routeState = FeatureHubRouteState()

        routeState.openSetup()
        routeState.grantBluetoothPermission()
        routeState.discoverDevice()
        routeState.connectDiscoveredDevice()
        routeState.confirmDeviceConnection()

        val route = routeState.route as FeatureHubRoute.Setup
        assertEquals(PairingStep.CONNECTED, route.pairingState.step)
        assertEquals("AirHealth Breath Sensor", route.pairingState.discoveredDevice?.name)
        assertEquals("setup/connected", route.routeId)
    }

    @Test
    fun permissionDeniedAndTimeoutStatesRecoverCleanly() {
        val routeState = FeatureHubRouteState()

        routeState.openSetup()
        routeState.denyBluetoothPermission()

        var route = routeState.route as FeatureHubRoute.Setup
        assertEquals(PairingStep.PERMISSION_DENIED, route.pairingState.step)
        assertTrue(route.pairingState.recoveryMessage?.contains("Bluetooth access") == true)

        routeState.retrySetupAfterFailure()
        routeState.grantBluetoothPermission()
        routeState.markDiscoveryTimeout()

        route = routeState.route as FeatureHubRoute.Setup
        assertEquals(PairingStep.TIMEOUT, route.pairingState.step)

        routeState.restartDiscovery()

        route = routeState.route as FeatureHubRoute.Setup
        assertEquals(PairingStep.DISCOVERING, route.pairingState.step)
    }

    @Test
    fun claimFailureRecoversAndModeSelectionFinishesSetup() {
        val routeState = FeatureHubRouteState()

        routeState.openSetup()
        routeState.grantBluetoothPermission()
        routeState.discoverDevice()
        routeState.connectDiscoveredDevice()
        routeState.confirmDeviceConnection()
        routeState.startClaimDevice()
        routeState.failClaimDevice()

        var route = routeState.route as FeatureHubRoute.Setup
        assertEquals(PairingStep.CLAIM_FAILED, route.pairingState.step)
        assertTrue(route.pairingState.recoveryMessage?.contains("Retry claim") == true)

        routeState.retryClaimDevice()
        routeState.startClaimDevice()
        routeState.completeClaimDevice()
        routeState.selectSetupMode(SetupMode.FAT_BURNING)

        route = routeState.route as FeatureHubRoute.Setup
        assertEquals(PairingStep.SETUP_COMPLETE, route.pairingState.step)
        assertEquals(SetupMode.FAT_BURNING, route.pairingState.selectedMode)
        assertEquals("Primary AirHealth account", route.pairingState.claimOwnerLabel)
    }
}
