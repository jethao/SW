package com.airhealth.app

enum class FeatureKind(
    val routeId: String,
    val title: String,
    val subtitle: String,
) {
    ORAL_HEALTH(
        routeId = "oral_health",
        title = "Oral Health",
        subtitle = "Track oral-health trends over time.",
    ),
    FAT_BURNING(
        routeId = "fat_burning",
        title = "Fat Burning",
        subtitle = "Follow repeated breath sessions and best-delta progress.",
    ),
}

enum class FeatureAction(
    val routeId: String,
    val title: String,
) {
    SET_GOALS("set_goals", "Set Goals"),
    VIEW_HISTORY("view_history", "View History"),
    MEASURE("measure", "Measure"),
    GET_SUGGESTION("get_suggestion", "Get Suggestion"),
    CONSULT_PROFESSIONALS("consult_professionals", "Consult Professionals"),
}

data class SelectedFeatureContext(
    val feature: FeatureKind,
    val lastVisitedRouteId: String,
)

sealed class FeatureHubRoute {
    data object Home : FeatureHubRoute()

    data class Setup(val pairingState: PairingFlowState) : FeatureHubRoute()

    data class Feature(val context: SelectedFeatureContext) : FeatureHubRoute()

    data class Action(
        val context: SelectedFeatureContext,
        val action: FeatureAction,
    ) : FeatureHubRoute()

    val routeId: String
        get() = when (this) {
            Home -> "home"
            is Setup -> "setup/${pairingState.step.routeId}"
            is Feature -> "feature_hub/${context.feature.routeId}"
            is Action -> "feature_hub/${context.feature.routeId}/${action.routeId}"
        }
}

class FeatureHubRouteState(
    initialRoute: FeatureHubRoute = FeatureHubRoute.Home,
) {
    var route: FeatureHubRoute = initialRoute
        private set

    var actionLockState: ActionLockState = ActionLockState()
        private set

    val actionGateAnalytics = ActionGateAnalytics()
    val pairingRecoveryAnalytics = PairingRecoveryAnalytics()

    val lastBlockedActionAttempt: BlockedActionAttempt?
        get() = actionLockState.blockedAttempt

    fun openFeature(feature: FeatureKind) {
        actionLockState = actionLockState.clearBlockedAttempt()
        route = FeatureHubRoute.Feature(
            SelectedFeatureContext(
                feature = feature,
                lastVisitedRouteId = FeatureHubRoute.Home.routeId,
            ),
        )
    }

    fun openSetup() {
        route = FeatureHubRoute.Setup(PairingFlowState.permissionPrimer())
    }

    fun denyBluetoothPermission() {
        route = FeatureHubRoute.Setup(
            PairingFlowState(
                step = PairingStep.PERMISSION_DENIED,
                recoveryMessage = "Bluetooth access is required to discover your AirHealth device.",
            ),
        )
    }

    fun grantBluetoothPermission() {
        route = FeatureHubRoute.Setup(PairingFlowState.discovering())
    }

    fun discoverDevice() {
        route = FeatureHubRoute.Setup(
            PairingFlowState(
                step = PairingStep.DEVICE_DISCOVERED,
                discoveredDevice = PairingFlowState.defaultDevice(),
            ),
        )
    }

    fun connectDiscoveredDevice() {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return
        val device = currentRoute.pairingState.discoveredDevice ?: return
        route = FeatureHubRoute.Setup(
            PairingFlowState(
                step = PairingStep.CONNECTING,
                discoveredDevice = device,
            ),
        )
    }

    fun markDeviceIncompatible() {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return
        pairingRecoveryAnalytics.record(PairingStep.INCOMPATIBLE, PairingRecoveryAction.SURFACED)
        route = FeatureHubRoute.Setup(
            currentRoute.pairingState.copy(
                step = PairingStep.INCOMPATIBLE,
                recoveryMessage = "This device is not supported in the AirHealth consumer app. Try another AirHealth device or check for an app update.",
            ),
        )
    }

    fun markDeviceNotReady() {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return
        pairingRecoveryAnalytics.record(PairingStep.NOT_READY, PairingRecoveryAction.SURFACED)
        route = FeatureHubRoute.Setup(
            currentRoute.pairingState.copy(
                step = PairingStep.NOT_READY,
                recoveryMessage = "This device is not ready for setup yet. Keep it nearby, charge it if needed, and try again in a moment.",
            ),
        )
    }

    fun confirmDeviceConnection() {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return
        val device = currentRoute.pairingState.discoveredDevice ?: return
        route = FeatureHubRoute.Setup(
            PairingFlowState(
                step = PairingStep.CONNECTED,
                discoveredDevice = device,
            ),
        )
    }

    fun startClaimDevice() {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return
        val device = currentRoute.pairingState.discoveredDevice ?: return
        route = FeatureHubRoute.Setup(
            PairingFlowState(
                step = PairingStep.CLAIMING,
                discoveredDevice = device,
            ),
        )
    }

    fun completeClaimDevice() {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return
        val device = currentRoute.pairingState.discoveredDevice ?: return
        route = FeatureHubRoute.Setup(
            PairingFlowState(
                step = PairingStep.MODE_SELECTION,
                discoveredDevice = device,
                claimOwnerLabel = "Primary AirHealth account",
            ),
        )
    }

    fun failClaimDevice() {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return
        pairingRecoveryAnalytics.record(PairingStep.CLAIM_FAILED, PairingRecoveryAction.SURFACED)
        route = FeatureHubRoute.Setup(
            currentRoute.pairingState.copy(
                step = PairingStep.CLAIM_FAILED,
                recoveryMessage = "We could not bind this device yet. Retry claim to continue setup.",
            ),
        )
    }

    fun retryClaimDevice() {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return
        val device = currentRoute.pairingState.discoveredDevice ?: return
        if (currentRoute.pairingState.step == PairingStep.CLAIM_FAILED) {
            pairingRecoveryAnalytics.record(PairingStep.CLAIM_FAILED, PairingRecoveryAction.RETRY)
        }
        route = FeatureHubRoute.Setup(
            PairingFlowState(
                step = PairingStep.CLAIMING,
                discoveredDevice = device,
            ),
        )
    }

    fun retryDeviceReadinessCheck() {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return
        val device = currentRoute.pairingState.discoveredDevice ?: return
        if (currentRoute.pairingState.step == PairingStep.NOT_READY) {
            pairingRecoveryAnalytics.record(PairingStep.NOT_READY, PairingRecoveryAction.RETRY)
        }
        route = FeatureHubRoute.Setup(
            PairingFlowState(
                step = PairingStep.CONNECTING,
                discoveredDevice = device,
            ),
        )
    }

    fun selectSetupMode(mode: SetupMode) {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return
        val device = currentRoute.pairingState.discoveredDevice ?: return
        route = FeatureHubRoute.Setup(
            PairingFlowState(
                step = PairingStep.SETUP_COMPLETE,
                discoveredDevice = device,
                claimOwnerLabel = currentRoute.pairingState.claimOwnerLabel ?: "Primary AirHealth account",
                selectedMode = mode,
            ),
        )
    }

    fun markDiscoveryTimeout() {
        pairingRecoveryAnalytics.record(PairingStep.TIMEOUT, PairingRecoveryAction.SURFACED)
        route = FeatureHubRoute.Setup(
            PairingFlowState(
                step = PairingStep.TIMEOUT,
                recoveryMessage = "No AirHealth device responded before the scan timeout. Retry to scan again.",
            ),
        )
    }

    fun retrySetupAfterFailure() {
        route = FeatureHubRoute.Setup(PairingFlowState.permissionPrimer())
    }

    fun restartDiscovery() {
        currentRecoverableFailureStep()?.let { failureStep ->
            pairingRecoveryAnalytics.record(failureStep, PairingRecoveryAction.RETRY)
        }
        route = FeatureHubRoute.Setup(PairingFlowState.discovering())
    }

    fun exitSetup() {
        currentRecoverableFailureStep()?.let { failureStep ->
            pairingRecoveryAnalytics.record(failureStep, PairingRecoveryAction.EXIT)
        }
        route = FeatureHubRoute.Home
    }

    fun openAction(action: FeatureAction) {
        val currentContext = currentFeatureContext() ?: return
        val requestedAction = ManagedAction.fromFeatureAction(action)
        actionLockState = actionLockState.tryAcquire(
            feature = currentContext.feature,
            action = requestedAction,
        )

        actionLockState.blockedAttempt?.let { blockedAttempt ->
            actionGateAnalytics.recordBlocked(blockedAttempt)
            return
        }

        actionGateAnalytics.recordAllowed(
            feature = currentContext.feature,
            requestedAction = requestedAction,
        )

        route = FeatureHubRoute.Action(
            context = currentContext.copy(
                lastVisitedRouteId = "feature_hub/${currentContext.feature.routeId}",
            ),
            action = action,
        )
    }

    fun returnToFeature() {
        val currentActionRoute = route as? FeatureHubRoute.Action ?: return
        actionLockState = actionLockState.release()
        route = FeatureHubRoute.Feature(currentActionRoute.context)
    }

    fun returnHome() {
        actionLockState = when (route) {
            is FeatureHubRoute.Action -> actionLockState.release()
            else -> actionLockState.clearBlockedAttempt()
        }
        route = FeatureHubRoute.Home
    }

    fun activeManagedAction(): ManagedAction? {
        return actionLockState.activeAction
    }

    private fun currentFeatureContext(): SelectedFeatureContext? {
        return when (val currentRoute = route) {
            is FeatureHubRoute.Setup -> null
            is FeatureHubRoute.Feature -> currentRoute.context
            is FeatureHubRoute.Action -> currentRoute.context
            FeatureHubRoute.Home -> null
        }
    }

    private fun currentRecoverableFailureStep(): PairingStep? {
        val currentRoute = route as? FeatureHubRoute.Setup ?: return null
        return when (currentRoute.pairingState.step) {
            PairingStep.CLAIM_FAILED,
            PairingStep.INCOMPATIBLE,
            PairingStep.NOT_READY,
            PairingStep.TIMEOUT -> currentRoute.pairingState.step
            else -> null
        }
    }
}
