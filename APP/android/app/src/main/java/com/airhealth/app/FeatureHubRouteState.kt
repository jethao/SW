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
    var consultDirectoryCacheState: ConsultDirectoryCacheState = ConsultDirectoryCacheState()
        private set
    var oralMeasurementFlowState: OralMeasurementFlowState = OralMeasurementFlowState()
        private set
    var fatMeasurementFlowState: FatMeasurementFlowState = FatMeasurementFlowState()
        private set
    var measurementRecoveryState: MeasurementRecoveryState? = null
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

    fun consultDirectoryFor(feature: FeatureKind): FeatureConsultDirectory? {
        return consultDirectoryCacheState.directoryFor(feature)
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

    fun refreshConsultDirectory(
        feature: FeatureKind,
        localeTag: String = "en-US",
    ) {
        consultDirectoryCacheState = consultDirectoryCacheState.refresh(
            feature = feature,
            localeTag = localeTag,
            cachedAtEpochMillis = currentTimeMillis(),
        )
    }

    fun openFeature(feature: FeatureKind) {
        actionLockState = actionLockState.clearBlockedAttempt()
        if (feature != FeatureKind.ORAL_HEALTH) {
            oralMeasurementFlowState = OralMeasurementFlowState()
        }
        if (feature != FeatureKind.FAT_BURNING) {
            fatMeasurementFlowState = FatMeasurementFlowState()
        }
        measurementRecoveryState = measurementRecoveryState
            ?.takeIf { it.feature == feature && it.stage == MeasurementRecoveryStage.RECOVERED }
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
        oralMeasurementFlowState = OralMeasurementFlowState()
        fatMeasurementFlowState = FatMeasurementFlowState()
        measurementRecoveryState = null
        route = FeatureHubRoute.Home
    }

    fun startOralMeasurement() {
        val currentContext = currentFeatureContext() ?: return
        if (currentContext.feature != FeatureKind.ORAL_HEALTH) return
        measurementRecoveryState = null
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.start(
            state = oralMeasurementFlowState,
            sessionId = nextMeasurementSessionId(currentContext.feature),
        )
    }

    fun markOralWarmupPassed() {
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.markWarmupPassed(
            state = oralMeasurementFlowState,
        )
    }

    fun markOralWarmupFailed() {
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.markWarmupFailed(
            state = oralMeasurementFlowState,
        )
    }

    fun completeOralMeasurement() {
        val nextScore = 52 + (oralMeasurementFlowState.baselineProgress.completedValidSessions * 4)
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.complete(
            state = oralMeasurementFlowState,
            oralHealthScore = nextScore.coerceAtMost(76),
        )
    }

    fun markOralInvalidSample() {
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.markInvalidSample(
            state = oralMeasurementFlowState,
        )
    }

    fun cancelOralMeasurement() {
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.cancel(
            state = oralMeasurementFlowState,
        )
    }

    fun startFatMeasurement() {
        val currentContext = currentFeatureContext() ?: return
        if (currentContext.feature != FeatureKind.FAT_BURNING) return
        measurementRecoveryState = null
        fatMeasurementFlowState = FatMeasurementFlowCoordinator.start(
            state = fatMeasurementFlowState,
            sessionId = nextMeasurementSessionId(currentContext.feature),
        )
    }

    fun lockFatBaseline() {
        fatMeasurementFlowState = FatMeasurementFlowCoordinator.completeCoachingWithFirstReading(
            state = fatMeasurementFlowState,
            deltaPercent = 0,
        )
    }

    fun recordNextFatReading(deltaPercent: Int) {
        fatMeasurementFlowState = FatMeasurementFlowCoordinator.recordReading(
            state = fatMeasurementFlowState,
            deltaPercent = deltaPercent,
        )
    }

    fun requestFatFinish() {
        fatMeasurementFlowState = FatMeasurementFlowCoordinator.requestFinish(
            state = fatMeasurementFlowState,
        )
    }

    fun completeFatMeasurement(finalDeltaPercent: Int) {
        fatMeasurementFlowState = FatMeasurementFlowCoordinator.complete(
            state = fatMeasurementFlowState,
            finalDeltaPercent = finalDeltaPercent,
        )
    }

    fun failFatMeasurement(reasonCode: String) {
        val message = when (reasonCode) {
            "disconnect" -> "The device disconnected before the final summary arrived. Retry without saving a session result."
            else -> "The reading was not valid. Retry without saving a result."
        }
        fatMeasurementFlowState = FatMeasurementFlowCoordinator.fail(
            state = fatMeasurementFlowState,
            reasonCode = reasonCode,
            recoveryMessage = message,
        )
    }

    fun cancelFatMeasurement() {
        fatMeasurementFlowState = FatMeasurementFlowCoordinator.cancel(
            state = fatMeasurementFlowState,
        )
    }

    fun measurementRecoveryStateFor(feature: FeatureKind): MeasurementRecoveryState? {
        return measurementRecoveryState?.takeIf { it.feature == feature }
    }

    fun disconnectActiveMeasurement() {
        val currentContext = currentFeatureContext() ?: return
        when (currentContext.feature) {
            FeatureKind.ORAL_HEALTH -> {
                val session = oralMeasurementFlowState.activeSession ?: return
                val disconnectedSession = MeasurementSessionCoordinator.reduce(
                    state = session,
                    event = MeasurementBleEvent.DeviceDisconnected(replayRequired = true),
                )
                oralMeasurementFlowState = oralMeasurementFlowState.copy(
                    activeSession = disconnectedSession,
                    recoveryMessage = "Session interrupted. Reconnect to replay the device result before failing the session.",
                )
                measurementRecoveryState = MeasurementRecoveryState.interrupted(
                    feature = currentContext.feature,
                    sessionId = disconnectedSession.sessionId,
                )
            }

            FeatureKind.FAT_BURNING -> {
                val session = fatMeasurementFlowState.activeSession ?: return
                val disconnectedSession = MeasurementSessionCoordinator.reduce(
                    state = session,
                    event = MeasurementBleEvent.DeviceDisconnected(replayRequired = true),
                )
                fatMeasurementFlowState = fatMeasurementFlowState.copy(
                    activeSession = disconnectedSession,
                    recoveryMessage = "Session interrupted. Reconnect to replay the device result before failing the session.",
                    finishRequested = false,
                )
                measurementRecoveryState = MeasurementRecoveryState.interrupted(
                    feature = currentContext.feature,
                    sessionId = disconnectedSession.sessionId,
                )
            }
        }
    }

    fun beginReconnectReplay() {
        val recovery = measurementRecoveryState ?: return
        measurementRecoveryState = recovery.copy(
            stage = MeasurementRecoveryStage.RECONNECTING,
            statusMessage = "Reconnected. Querying the device for the terminal status by session ID.",
        )
    }

    fun recoverMeasurementReplay() {
        val recovery = measurementRecoveryState ?: return
        when (recovery.feature) {
            FeatureKind.ORAL_HEALTH -> {
                val score = 57 + oralMeasurementFlowState.baselineProgress.completedValidSessions
                oralMeasurementFlowState = OralMeasurementFlowCoordinator.complete(
                    state = oralMeasurementFlowState,
                    oralHealthScore = score,
                ).copy(
                    recoveryMessage = "Recovered the completed oral result after reconnect.",
                )
                measurementRecoveryState = measurementRecoveryStateFor(recovery.feature)?.copy(
                    stage = MeasurementRecoveryStage.RECOVERED,
                    statusMessage = "Replay succeeded. The device returned a completed oral result for this session.",
                    recoveredResultToken = oralMeasurementFlowState.activeSession?.terminalSummary?.resultToken,
                )
            }

            FeatureKind.FAT_BURNING -> {
                val finalDelta = ((fatMeasurementFlowState.bestDeltaPercent ?: 9) - 2).coerceAtLeast(4)
                fatMeasurementFlowState = FatMeasurementFlowCoordinator.complete(
                    state = fatMeasurementFlowState.copy(finishRequested = true),
                    finalDeltaPercent = finalDelta,
                ).copy(
                    recoveryMessage = "Recovered the completed fat-burning summary after reconnect.",
                )
                measurementRecoveryState = measurementRecoveryStateFor(recovery.feature)?.copy(
                    stage = MeasurementRecoveryStage.RECOVERED,
                    statusMessage = "Replay succeeded. The device returned a completed fat-burning summary for this session.",
                    recoveredResultToken = fatMeasurementFlowState.activeSession?.terminalSummary?.resultToken,
                )
            }
        }
    }

    fun failMeasurementReplay() {
        val recovery = measurementRecoveryState ?: return
        when (recovery.feature) {
            FeatureKind.ORAL_HEALTH -> {
                val session = oralMeasurementFlowState.activeSession ?: return
                oralMeasurementFlowState = oralMeasurementFlowState.copy(
                    activeSession = MeasurementSessionCoordinator.reduce(
                        state = session,
                        event = MeasurementBleEvent.MeasurementFailed(reasonCode = "replay_unavailable"),
                    ),
                    latestResult = null,
                    recoveryMessage = "Replay did not return a completed result. This session stays failed and unsaved.",
                )
            }

            FeatureKind.FAT_BURNING -> {
                val session = fatMeasurementFlowState.activeSession ?: return
                fatMeasurementFlowState = fatMeasurementFlowState.copy(
                    activeSession = MeasurementSessionCoordinator.reduce(
                        state = session,
                        event = MeasurementBleEvent.MeasurementFailed(reasonCode = "replay_unavailable"),
                    ),
                    latestResult = null,
                    recoveryMessage = "Replay did not return a completed summary. This session stays failed and unsaved.",
                    finishRequested = false,
                )
            }
        }

        measurementRecoveryState = measurementRecoveryStateFor(recovery.feature)?.copy(
            stage = MeasurementRecoveryStage.FAILED,
            statusMessage = "Replay failed. No completed device result could be recovered for this session.",
        )
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

    private fun nextMeasurementSessionId(feature: FeatureKind): String {
        sampleSessionOrdinal += 1
        return "${feature.routeId}-session-$sampleSessionOrdinal"
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
