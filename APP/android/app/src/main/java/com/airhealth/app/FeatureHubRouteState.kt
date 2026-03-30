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
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var entitlementCacheState: EntitlementCacheState = EntitlementCacheState()
    private var sampleSessionOrdinal: Long = 0L
    var goalCacheState: GoalCacheState = GoalCacheState()
        private set
    var suggestionCacheState: SuggestionCacheState = SuggestionCacheState()
        private set
    var sessionHistoryStoreState: SessionHistoryStoreState = SessionHistoryStoreState()
        private set
    var sessionSyncQueueState: SessionSyncQueueState = SessionSyncQueueState()
        private set
    var exportAuditStoreState: ExportAuditStoreState = ExportAuditStoreState()
        private set
    var exportPermissionStoreState: HealthExportPermissionStoreState = HealthExportPermissionStoreState()
        private set

    var route: FeatureHubRoute = initialRoute
        private set

    var actionLockState: ActionLockState = ActionLockState()
        private set

    val actionGateAnalytics = ActionGateAnalytics()
    val pairingRecoveryAnalytics = PairingRecoveryAnalytics()

    val lastBlockedActionAttempt: BlockedActionAttempt?
        get() = actionLockState.blockedAttempt

    val effectiveEntitlement: EffectiveEntitlement
        get() = EntitlementEvaluator.deriveEffectiveState(
            state = entitlementCacheState,
            nowEpochMillis = currentTimeMillis(),
        )

    fun goalFor(feature: FeatureKind): FeatureGoal? {
        return goalCacheState.goalFor(feature)
    }

    fun suggestionFor(feature: FeatureKind): FeatureSuggestion? {
        return suggestionCacheState.suggestionFor(feature)
    }

    fun historyProjectionFor(feature: FeatureKind): FeatureHistoryProjection {
        return sessionHistoryStoreState.projectionFor(feature)
    }

    fun syncQueueProjectionFor(feature: FeatureKind): FeatureSyncQueueProjection {
        return sessionSyncQueueState.projectionFor(feature, currentTimeMillis())
    }

    fun activeSyncJobFor(feature: FeatureKind): PersistedSessionSyncJob? {
        return sessionSyncQueueState.activeJobFor(feature)
    }

    fun exportAuditSurfaceFor(
        feature: FeatureKind,
        platform: HealthExportPlatform,
    ): ExportAuditSurfaceState {
        return exportAuditStoreState.surfaceFor(
            feature = feature,
            permissionState = exportPermissionStoreState.permissionFor(platform),
        )
    }

    fun setExportPermission(
        platform: HealthExportPlatform,
        permissionState: HealthExportPermissionState,
    ) {
        exportPermissionStoreState = exportPermissionStoreState.setPermission(platform, permissionState)
    }

    fun applyGoalTemplate(template: GoalDraftTemplate) {
        goalCacheState = goalCacheState.upsert(
            feature = template.feature,
            summary = template.summary,
            targetLabel = template.targetLabel,
            cadenceLabel = template.cadenceLabel,
            updatedAtEpochMillis = currentTimeMillis(),
        )
    }

    fun clearGoal(feature: FeatureKind) {
        goalCacheState = goalCacheState.remove(feature)
    }

    fun refreshSuggestion(
        feature: FeatureKind,
        entryPoint: SuggestionEntryPoint,
    ) {
        suggestionCacheState = suggestionCacheState.refresh(
            feature = feature,
            entryPoint = entryPoint,
            templates = suggestionTemplatesFor(
                feature = feature,
                goal = goalFor(feature),
            ),
            cachedAtEpochMillis = currentTimeMillis(),
        )
    }

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
        val entitlementBlockReason = entitlementBlockReason(
            action = requestedAction,
            entitlement = effectiveEntitlement,
        )

        if (entitlementBlockReason != null) {
            actionLockState = actionLockState.blockByEntitlement(
                feature = currentContext.feature,
                action = requestedAction,
                reasonCode = entitlementBlockReason,
            )
            actionLockState.blockedAttempt?.let { blockedAttempt ->
                actionGateAnalytics.recordBlocked(blockedAttempt)
            }
            return
        }

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

    fun replaceEntitlementCacheState(state: EntitlementCacheState) {
        entitlementCacheState = state
    }

    fun recordDemoCompletedSession(feature: FeatureKind) {
        sampleSessionOrdinal += 1
        val session = synthesizeCompletedSession(
            feature = feature,
            ordinal = sampleSessionOrdinal,
        )
        val reconciliation = SessionSyncReconciler.recordCompletedSession(
            historyStoreState = sessionHistoryStoreState,
            syncQueueState = sessionSyncQueueState,
            session = session,
            recordedAtEpochMillis = currentTimeMillis(),
        )
        sessionHistoryStoreState = reconciliation.historyStoreState
        sessionSyncQueueState = reconciliation.syncQueueState
    }

    fun beginNextSyncAttempt(): PersistedSessionSyncJob? {
        val dispatch = sessionSyncQueueState.beginNextEligibleAttempt(currentTimeMillis())
        sessionSyncQueueState = dispatch.queueState
        return dispatch.dispatchedJob
    }

    fun markSyncAttemptSucceeded(sessionId: String) {
        val reconciliation = SessionSyncReconciler.markSessionSynced(
            historyStoreState = sessionHistoryStoreState,
            syncQueueState = sessionSyncQueueState,
            sessionId = sessionId,
        )
        sessionHistoryStoreState = reconciliation.historyStoreState
        sessionSyncQueueState = reconciliation.syncQueueState
    }

    fun markSyncAttemptFailed(
        sessionId: String,
        reasonCode: String,
    ) {
        sessionSyncQueueState = sessionSyncQueueState.markAttemptFailed(
            sessionId = sessionId,
            nowEpochMillis = currentTimeMillis(),
            reasonCode = reasonCode,
        )
    }

    fun exportLatestCompletedSummary(
        feature: FeatureKind,
        platform: HealthExportPlatform,
    ): CompletedSummaryExportPayload? {
        val latestRecord = sessionHistoryStoreState.records
            .filter { it.feature == feature }
            .maxByOrNull { it.recordedAtEpochMillis }
            ?: return null

        if (exportPermissionStoreState.permissionFor(platform) != HealthExportPermissionState.GRANTED) {
            exportAuditStoreState = exportAuditStoreState.append(
                ExportAuditRecord(
                    auditId = "${platform.wireValue}:${latestRecord.sessionId}:${currentTimeMillis()}",
                    feature = feature,
                    sessionId = latestRecord.sessionId,
                    platform = platform,
                    status = ExportAuditStatus.FAILED,
                    recordedAtEpochMillis = currentTimeMillis(),
                    exportedResultToken = latestRecord.resultToken,
                    failureReason = "permission_denied",
                ),
            )
            return null
        }

        val payload = HealthExportAdapter.payloadFor(
            record = latestRecord,
            platform = platform,
        )
        exportAuditStoreState = exportAuditStoreState.append(
            ExportAuditRecord(
                auditId = "${platform.wireValue}:${latestRecord.sessionId}:${currentTimeMillis()}",
                feature = feature,
                sessionId = latestRecord.sessionId,
                platform = platform,
                status = ExportAuditStatus.SUCCEEDED,
                recordedAtEpochMillis = currentTimeMillis(),
                exportedResultToken = latestRecord.resultToken,
                failureReason = null,
            ),
        )
        return payload
    }

    fun failLatestCompletedSummaryExport(
        feature: FeatureKind,
        platform: HealthExportPlatform,
        reasonCode: String,
    ) {
        val latestRecord = sessionHistoryStoreState.records
            .filter { it.feature == feature }
            .maxByOrNull { it.recordedAtEpochMillis }
            ?: return

        exportAuditStoreState = exportAuditStoreState.append(
            ExportAuditRecord(
                auditId = "${platform.wireValue}:${latestRecord.sessionId}:${currentTimeMillis()}",
                feature = feature,
                sessionId = latestRecord.sessionId,
                platform = platform,
                status = ExportAuditStatus.FAILED,
                recordedAtEpochMillis = currentTimeMillis(),
                exportedResultToken = latestRecord.resultToken,
                failureReason = reasonCode,
            ),
        )
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

    private fun entitlementBlockReason(
        action: ManagedAction,
        entitlement: EffectiveEntitlement,
    ): ActionLockReasonCode? {
        return when (action) {
            ManagedAction.MEASURE ->
                if (entitlement.canStartNewSessions) null else entitlement.reasonCode()

            ManagedAction.SET_GOALS ->
                if (entitlement.canEditGoals) null else entitlement.reasonCode()

            ManagedAction.GET_SUGGESTION ->
                if (entitlement.canRequestLiveSuggestions) null else entitlement.reasonCode()

            ManagedAction.VIEW_HISTORY,
            ManagedAction.CONSULT_PROFESSIONALS,
            ManagedAction.SETUP -> null
        }
    }

    private fun EffectiveEntitlement.reasonCode(): ActionLockReasonCode {
        return when (mode) {
            EffectiveEntitlementMode.ACTIVE -> ActionLockReasonCode.CONFLICTING_ACTION_IN_PROGRESS
            EffectiveEntitlementMode.TEMPORARY_ACCESS -> ActionLockReasonCode.TEMPORARY_ACCESS_RESTRICTION
            EffectiveEntitlementMode.READ_ONLY -> ActionLockReasonCode.READ_ONLY_MODE_RESTRICTION
        }
    }

    private fun synthesizeCompletedSession(
        feature: FeatureKind,
        ordinal: Long,
    ): MeasurementSessionState {
        val sessionId = "${feature.routeId}-session-$ordinal"
        val resultToken = "${feature.routeId.take(4)}-result-$ordinal"
        return MeasurementSessionCoordinator.reduce(
            MeasurementSessionCoordinator.reduce(
                MeasurementSessionCoordinator.reduce(
                    MeasurementSessionState.begin(
                        sessionId = sessionId,
                        feature = feature,
                    ),
                    MeasurementBleEvent.MeasurementStarted,
                ),
                MeasurementBleEvent.TerminalReadingAvailable(
                    MeasurementTerminalSummary(resultToken = resultToken),
                ),
            ),
            MeasurementBleEvent.TerminalReadingConfirmed,
        )
    }
}
