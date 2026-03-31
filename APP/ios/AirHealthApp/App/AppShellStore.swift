import SwiftUI

enum ManagedAction: String {
    case setup = "setup"
    case setGoals = "set_goals"
    case viewHistory = "view_history"
    case measure = "measure"
    case getSuggestion = "get_suggestion"
    case consultProfessionals = "consult_professionals"

    var title: String {
        switch self {
        case .setup:
            return "Setup"
        case .setGoals:
            return "Set Goals"
        case .viewHistory:
            return "View History"
        case .measure:
            return "Measure"
        case .getSuggestion:
            return "Get Suggestion"
        case .consultProfessionals:
            return "Consult Professionals"
        }
    }

    static func from(_ action: FeatureAction) -> ManagedAction {
        switch action {
        case .setGoals:
            return .setGoals
        case .viewHistory:
            return .viewHistory
        case .measure:
            return .measure
        case .getSuggestion:
            return .getSuggestion
        case .consultProfessionals:
            return .consultProfessionals
        }
    }
}

enum ActionLockReasonCode: String {
    case conflictingActionInProgress = "conflicting_action_in_progress"
    case temporaryAccessRestriction = "temporary_access_restriction"
    case readOnlyModeRestriction = "read_only_mode_restriction"
}

enum ActionGateOutcome: String {
    case allowed = "allowed"
    case blocked = "blocked"
}

struct ActionGateEvent {
    let feature: String
    let requestedAction: String
    let outcome: String
    let activeAction: String?
    let reasonCode: String?

    var payload: [String: String] {
        var payload = [
            "feature": feature,
            "requested_action": requestedAction,
            "outcome": outcome,
        ]

        if let activeAction {
            payload["active_action"] = activeAction
        }

        if let reasonCode {
            payload["reason_code"] = reasonCode
        }

        return payload
    }
}

struct BlockedActionAttempt {
    let requestedFeature: FeatureKind
    let requestedAction: ManagedAction
    let activeFeature: FeatureKind?
    let activeAction: ManagedAction?
    let reasonCode: ActionLockReasonCode

    var message: String {
        switch reasonCode {
        case .conflictingActionInProgress:
            return "Finish \(activeAction?.title ?? "the current action") for \(activeFeature?.title ?? "this feature") before starting \(requestedAction.title)."
        case .temporaryAccessRestriction:
            return "\(requestedAction.title) is unavailable during Temporary access. Reconnect to verify entitlement before continuing."
        case .readOnlyModeRestriction:
            return "\(requestedAction.title) is unavailable in Read-only mode. Restore active entitlement to continue."
        }
    }
}

struct ActionLockState {
    var activeFeature: FeatureKind?
    var activeAction: ManagedAction?
    var blockedAttempt: BlockedActionAttempt?

    mutating func tryAcquire(feature: FeatureKind, action: ManagedAction) -> Bool {
        if let activeAction, let activeFeature {
            if activeAction == action && activeFeature == feature {
                blockedAttempt = nil
                return true
            }

            blockedAttempt = BlockedActionAttempt(
                requestedFeature: feature,
                requestedAction: action,
                activeFeature: activeFeature,
                activeAction: activeAction,
                reasonCode: .conflictingActionInProgress
            )
            return false
        }

        activeFeature = feature
        activeAction = action
        blockedAttempt = nil
        return true
    }

    mutating func release() {
        activeFeature = nil
        activeAction = nil
        blockedAttempt = nil
    }

    mutating func clearBlockedAttempt() {
        blockedAttempt = nil
    }

    mutating func blockByEntitlement(
        feature: FeatureKind,
        action: ManagedAction,
        reasonCode: ActionLockReasonCode
    ) {
        blockedAttempt = BlockedActionAttempt(
            requestedFeature: feature,
            requestedAction: action,
            activeFeature: activeFeature,
            activeAction: activeAction,
            reasonCode: reasonCode
        )
    }
}

enum AppRoute {
    case home
    case setup(PairingFlowState)
    case featureHub(SelectedFeatureContext)
    case featureAction(SelectedFeatureContext, FeatureAction)

    var routeID: String {
        switch self {
        case .home:
            return "home"
        case let .setup(pairingState):
            return "setup/\(pairingState.step.rawValue)"
        case let .featureHub(context):
            return "feature_hub/\(context.feature.rawValue)"
        case let .featureAction(context, action):
            return "feature_hub/\(context.feature.rawValue)/\(action.rawValue)"
        }
    }
}

@MainActor
final class AppShellStore: ObservableObject {
    @Published private(set) var route: AppRoute = .home
    @Published private(set) var actionLockState = ActionLockState()
    @Published private(set) var actionGateEvents: [ActionGateEvent] = []
    @Published private(set) var pairingRecoveryEvents: [PairingRecoveryEvent] = []
    @Published private(set) var entitlementCacheState: EntitlementCacheState
    @Published private(set) var goalCacheState = GoalCacheState()
    @Published private(set) var suggestionCacheState = SuggestionCacheState()
    @Published private(set) var oralMeasurementFlowState = OralMeasurementFlowState()
    @Published private(set) var sessionHistoryStoreState = SessionHistoryStoreState()
    @Published private(set) var sessionSyncQueueState = SessionSyncQueueState()
    @Published private(set) var exportAuditStoreState = ExportAuditStoreState()
    @Published private(set) var exportPermissionStoreState = HealthExportPermissionStoreState()

    private var sampleSessionOrdinal: Int64 = 0

    var lastBlockedActionAttempt: BlockedActionAttempt? {
        actionLockState.blockedAttempt
    }

    var activeManagedAction: ManagedAction? {
        actionLockState.activeAction
    }

    init(nowEpochMillis: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) {
        self.nowEpochMillis = nowEpochMillis
        self.entitlementCacheState = .empty()
    }

    var effectiveEntitlement: EffectiveEntitlement {
        EntitlementEvaluator.deriveEffectiveState(
            state: entitlementCacheState,
            nowEpochMillis: nowEpochMillis()
        )
    }

    func goal(for feature: FeatureKind) -> FeatureGoal? {
        goalCacheState.goal(for: feature)
    }

    func suggestion(for feature: FeatureKind) -> FeatureSuggestion? {
        suggestionCacheState.suggestion(for: feature)
    }

    func historyProjection(for feature: FeatureKind) -> FeatureHistoryProjection {
        sessionHistoryStoreState.projection(for: feature)
    }

    func syncQueueProjection(for feature: FeatureKind) -> FeatureSyncQueueProjection {
        sessionSyncQueueState.projection(for: feature, nowEpochMillis: nowEpochMillis())
    }

    func activeSyncJob(for feature: FeatureKind) -> PersistedSessionSyncJob? {
        sessionSyncQueueState.activeJob(for: feature)
    }

    func exportAuditSurface(
        for feature: FeatureKind,
        platform: HealthExportPlatform
    ) -> ExportAuditSurfaceState {
        exportAuditStoreState.surface(
            for: feature,
            permissionState: exportPermissionStoreState.permission(for: platform)
        )
    }

    func setExportPermission(
        _ permissionState: HealthExportPermissionState,
        for platform: HealthExportPlatform
    ) {
        exportPermissionStoreState = exportPermissionStoreState.setPermission(permissionState, for: platform)
    }

    func replaceEntitlementCacheState(_ state: EntitlementCacheState) {
        entitlementCacheState = state
    }

    func recordDemoCompletedSession(for feature: FeatureKind) {
        sampleSessionOrdinal += 1
        let session = synthesizeCompletedSession(
            feature: feature,
            ordinal: sampleSessionOrdinal
        )
        let reconciliation = SessionSyncReconciler.recordCompletedSession(
            historyStoreState: sessionHistoryStoreState,
            syncQueueState: sessionSyncQueueState,
            session: session,
            recordedAtEpochMillis: nowEpochMillis()
        )
        sessionHistoryStoreState = reconciliation.historyStoreState
        sessionSyncQueueState = reconciliation.syncQueueState
    }

    func beginNextSyncAttempt() -> PersistedSessionSyncJob? {
        let dispatch = sessionSyncQueueState.beginNextEligibleAttempt(nowEpochMillis: nowEpochMillis())
        sessionSyncQueueState = dispatch.queueState
        return dispatch.dispatchedJob
    }

    func markSyncAttemptSucceeded(sessionID: String) {
        let reconciliation = SessionSyncReconciler.markSessionSynced(
            historyStoreState: sessionHistoryStoreState,
            syncQueueState: sessionSyncQueueState,
            sessionID: sessionID
        )
        sessionHistoryStoreState = reconciliation.historyStoreState
        sessionSyncQueueState = reconciliation.syncQueueState
    }

    func markSyncAttemptFailed(sessionID: String, reasonCode: String) {
        sessionSyncQueueState = sessionSyncQueueState.markAttemptFailed(
            sessionID: sessionID,
            nowEpochMillis: nowEpochMillis(),
            reasonCode: reasonCode
        )
    }

    func exportLatestCompletedSummary(
        for feature: FeatureKind,
        platform: HealthExportPlatform
    ) -> CompletedSummaryExportPayload? {
        guard let latestRecord = sessionHistoryStoreState.records
            .filter({ $0.feature == feature })
            .max(by: { $0.recordedAtEpochMillis < $1.recordedAtEpochMillis }) else {
            return nil
        }

        guard exportPermissionStoreState.permission(for: platform) == .granted else {
            exportAuditStoreState = exportAuditStoreState.append(
                ExportAuditRecord(
                    auditID: "\(platform.rawValue):\(latestRecord.sessionID):\(nowEpochMillis())",
                    feature: feature,
                    sessionID: latestRecord.sessionID,
                    platform: platform,
                    status: .failed,
                    recordedAtEpochMillis: nowEpochMillis(),
                    exportedResultToken: latestRecord.resultToken,
                    failureReason: "permission_denied"
                )
            )
            return nil
        }

        let payload = HealthExportAdapter.payloadFor(record: latestRecord, platform: platform)
        exportAuditStoreState = exportAuditStoreState.append(
            ExportAuditRecord(
                auditID: "\(platform.rawValue):\(latestRecord.sessionID):\(nowEpochMillis())",
                feature: feature,
                sessionID: latestRecord.sessionID,
                platform: platform,
                status: .succeeded,
                recordedAtEpochMillis: nowEpochMillis(),
                exportedResultToken: latestRecord.resultToken,
                failureReason: nil
            )
        )
        return payload
    }

    func failLatestCompletedSummaryExport(
        for feature: FeatureKind,
        platform: HealthExportPlatform,
        reasonCode: String
    ) {
        guard let latestRecord = sessionHistoryStoreState.records
            .filter({ $0.feature == feature })
            .max(by: { $0.recordedAtEpochMillis < $1.recordedAtEpochMillis }) else {
            return
        }

        exportAuditStoreState = exportAuditStoreState.append(
            ExportAuditRecord(
                auditID: "\(platform.rawValue):\(latestRecord.sessionID):\(nowEpochMillis())",
                feature: feature,
                sessionID: latestRecord.sessionID,
                platform: platform,
                status: .failed,
                recordedAtEpochMillis: nowEpochMillis(),
                exportedResultToken: latestRecord.resultToken,
                failureReason: reasonCode
            )
        )
    }

    func applyGoalTemplate(_ template: GoalDraftTemplate) {
        goalCacheState = goalCacheState.upsert(
            feature: template.feature,
            summary: template.summary,
            targetLabel: template.targetLabel,
            cadenceLabel: template.cadenceLabel,
            updatedAtEpochMillis: nowEpochMillis()
        )
    }

    func clearGoal(feature: FeatureKind) {
        goalCacheState = goalCacheState.remove(feature: feature)
    }

    func refreshSuggestion(
        feature: FeatureKind,
        entryPoint: SuggestionEntryPoint
    ) {
        suggestionCacheState = suggestionCacheState.refresh(
            feature: feature,
            entryPoint: entryPoint,
            templates: suggestionTemplates(
                for: feature,
                goal: goal(for: feature)
            ),
            cachedAtEpochMillis: nowEpochMillis()
        )
    }

    func openFeature(_ feature: FeatureKind) {
        var nextLockState = actionLockState
        nextLockState.clearBlockedAttempt()
        actionLockState = nextLockState
        if feature != .oralHealth {
            oralMeasurementFlowState = OralMeasurementFlowState()
        }
        route = .featureHub(
            SelectedFeatureContext(
                feature: feature,
                lastVisitedRouteID: "home"
            )
        )
    }

    func openSetup() {
        route = .setup(.permissionPrimer())
    }

    func denyBluetoothPermission() {
        route = .setup(
            PairingFlowState(
                step: .permissionDenied,
                discoveredDevice: nil,
                recoveryMessage: "Bluetooth access is required to discover your AirHealth device.",
                claimOwnerLabel: nil,
                selectedMode: nil
            )
        )
    }

    func grantBluetoothPermission() {
        route = .setup(.discovering())
    }

    func discoverDevice() {
        route = .setup(
            PairingFlowState(
                step: .deviceDiscovered,
                discoveredDevice: .defaultDevice(),
                recoveryMessage: nil,
                claimOwnerLabel: nil,
                selectedMode: nil
            )
        )
    }

    func connectDiscoveredDevice() {
        guard case let .setup(pairingState) = route,
              let device = pairingState.discoveredDevice else {
            return
        }

        route = .setup(
            PairingFlowState(
                step: .connecting,
                discoveredDevice: device,
                recoveryMessage: nil,
                claimOwnerLabel: nil,
                selectedMode: nil
            )
        )
    }

    func markDeviceIncompatible() {
        guard case let .setup(pairingState) = route else {
            return
        }

        recordPairingRecovery(step: .incompatible, action: .surfaced)
        route = .setup(
            PairingFlowState(
                step: .incompatible,
                discoveredDevice: pairingState.discoveredDevice,
                recoveryMessage: "This device is not supported in the AirHealth consumer app. Try another AirHealth device or check for an app update.",
                claimOwnerLabel: pairingState.claimOwnerLabel,
                selectedMode: pairingState.selectedMode
            )
        )
    }

    func markDeviceNotReady() {
        guard case let .setup(pairingState) = route else {
            return
        }

        recordPairingRecovery(step: .notReady, action: .surfaced)
        route = .setup(
            PairingFlowState(
                step: .notReady,
                discoveredDevice: pairingState.discoveredDevice,
                recoveryMessage: "This device is not ready for setup yet. Keep it nearby, charge it if needed, and try again in a moment.",
                claimOwnerLabel: pairingState.claimOwnerLabel,
                selectedMode: pairingState.selectedMode
            )
        )
    }

    func confirmDeviceConnection() {
        guard case let .setup(pairingState) = route,
              let device = pairingState.discoveredDevice else {
            return
        }

        route = .setup(
            PairingFlowState(
                step: .connected,
                discoveredDevice: device,
                recoveryMessage: nil,
                claimOwnerLabel: nil,
                selectedMode: nil
            )
        )
    }

    func startClaimDevice() {
        guard case let .setup(pairingState) = route,
              let device = pairingState.discoveredDevice else {
            return
        }

        route = .setup(
            PairingFlowState(
                step: .claiming,
                discoveredDevice: device,
                recoveryMessage: nil,
                claimOwnerLabel: nil,
                selectedMode: nil
            )
        )
    }

    func completeClaimDevice() {
        guard case let .setup(pairingState) = route,
              let device = pairingState.discoveredDevice else {
            return
        }

        route = .setup(
            PairingFlowState(
                step: .modeSelection,
                discoveredDevice: device,
                recoveryMessage: nil,
                claimOwnerLabel: "Primary AirHealth account",
                selectedMode: nil
            )
        )
    }

    func failClaimDevice() {
        guard case let .setup(pairingState) = route else {
            return
        }

        recordPairingRecovery(step: .claimFailed, action: .surfaced)
        route = .setup(
            PairingFlowState(
                step: .claimFailed,
                discoveredDevice: pairingState.discoveredDevice,
                recoveryMessage: "We could not bind this device yet. Retry claim to continue setup.",
                claimOwnerLabel: pairingState.claimOwnerLabel,
                selectedMode: pairingState.selectedMode
            )
        )
    }

    func retryClaimDevice() {
        guard case let .setup(pairingState) = route,
              let device = pairingState.discoveredDevice else {
            return
        }

        if pairingState.step == .claimFailed {
            recordPairingRecovery(step: .claimFailed, action: .retry)
        }
        route = .setup(
            PairingFlowState(
                step: .claiming,
                discoveredDevice: device,
                recoveryMessage: nil,
                claimOwnerLabel: nil,
                selectedMode: nil
            )
        )
    }

    func retryDeviceReadinessCheck() {
        guard case let .setup(pairingState) = route,
              let device = pairingState.discoveredDevice else {
            return
        }

        if pairingState.step == .notReady {
            recordPairingRecovery(step: .notReady, action: .retry)
        }

        route = .setup(
            PairingFlowState(
                step: .connecting,
                discoveredDevice: device,
                recoveryMessage: nil,
                claimOwnerLabel: nil,
                selectedMode: nil
            )
        )
    }

    func selectSetupMode(_ mode: SetupMode) {
        guard case let .setup(pairingState) = route,
              let device = pairingState.discoveredDevice else {
            return
        }

        route = .setup(
            PairingFlowState(
                step: .setupComplete,
                discoveredDevice: device,
                recoveryMessage: nil,
                claimOwnerLabel: pairingState.claimOwnerLabel ?? "Primary AirHealth account",
                selectedMode: mode
            )
        )
    }

    func markDiscoveryTimeout() {
        recordPairingRecovery(step: .timeout, action: .surfaced)
        route = .setup(
            PairingFlowState(
                step: .timeout,
                discoveredDevice: nil,
                recoveryMessage: "No AirHealth device responded before the scan timeout. Retry to scan again.",
                claimOwnerLabel: nil,
                selectedMode: nil
            )
        )
    }

    func retrySetupAfterFailure() {
        route = .setup(.permissionPrimer())
    }

    func restartDiscovery() {
        if let failureStep = currentRecoverableFailureStep {
            recordPairingRecovery(step: failureStep, action: .retry)
        }
        route = .setup(.discovering())
    }

    func exitSetup() {
        if let failureStep = currentRecoverableFailureStep {
            recordPairingRecovery(step: failureStep, action: .exit)
        }
        route = .home
    }

    func openAction(_ action: FeatureAction) {
        guard let context = currentFeatureContext() else {
            return
        }

        let requestedAction = ManagedAction.from(action)
        if let reasonCode = entitlementBlockReason(
            for: requestedAction,
            entitlement: effectiveEntitlement
        ) {
            var nextLockState = actionLockState
            nextLockState.blockByEntitlement(
                feature: context.feature,
                action: requestedAction,
                reasonCode: reasonCode
            )
            actionLockState = nextLockState

            if let blockedAttempt = nextLockState.blockedAttempt {
                actionGateEvents.append(
                    ActionGateEvent(
                        feature: blockedAttempt.requestedFeature.rawValue,
                        requestedAction: blockedAttempt.requestedAction.rawValue,
                        outcome: ActionGateOutcome.blocked.rawValue,
                        activeAction: blockedAttempt.activeAction?.rawValue,
                        reasonCode: blockedAttempt.reasonCode.rawValue
                    )
                )
            }
            return
        }

        var nextLockState = actionLockState
        let wasAcquired = nextLockState.tryAcquire(
            feature: context.feature,
            action: requestedAction
        )
        actionLockState = nextLockState

        guard wasAcquired else {
            if let blockedAttempt = nextLockState.blockedAttempt {
                actionGateEvents.append(
                    ActionGateEvent(
                        feature: blockedAttempt.requestedFeature.rawValue,
                        requestedAction: blockedAttempt.requestedAction.rawValue,
                        outcome: ActionGateOutcome.blocked.rawValue,
                        activeAction: blockedAttempt.activeAction?.rawValue,
                        reasonCode: blockedAttempt.reasonCode.rawValue
                    )
                )
            }
            return
        }

        actionGateEvents.append(
            ActionGateEvent(
                feature: context.feature.rawValue,
                requestedAction: requestedAction.rawValue,
                outcome: ActionGateOutcome.allowed.rawValue,
                activeAction: nil,
                reasonCode: nil
            )
        )

        route = .featureAction(
            SelectedFeatureContext(
                feature: context.feature,
                lastVisitedRouteID: "feature_hub/\(context.feature.rawValue)"
            ),
            action
        )
    }

    func returnToFeature() {
        guard case let .featureAction(context, _) = route else {
            return
        }

        var nextLockState = actionLockState
        nextLockState.release()
        actionLockState = nextLockState
        route = .featureHub(context)
    }

    func returnHome() {
        var nextLockState = actionLockState
        if case .featureAction = route {
            nextLockState.release()
        } else {
            nextLockState.clearBlockedAttempt()
        }
        actionLockState = nextLockState

        oralMeasurementFlowState = OralMeasurementFlowState()
        route = .home
    }

    func startOralMeasurement() {
        guard let context = currentFeatureContext(),
              context.feature == .oralHealth else {
            return
        }

        oralMeasurementFlowState = OralMeasurementFlowCoordinator.start(
            state: oralMeasurementFlowState,
            sessionID: nextMeasurementSessionID(feature: context.feature)
        )
    }

    func markOralWarmupPassed() {
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.markWarmupPassed(
            state: oralMeasurementFlowState
        )
    }

    func markOralWarmupFailed() {
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.markWarmupFailed(
            state: oralMeasurementFlowState
        )
    }

    func completeOralMeasurement() {
        let nextScore = min(52 + (oralMeasurementFlowState.baselineProgress.completedValidSessions * 4), 76)
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.complete(
            state: oralMeasurementFlowState,
            oralHealthScore: nextScore
        )
    }

    func markOralInvalidSample() {
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.markInvalidSample(
            state: oralMeasurementFlowState
        )
    }

    func cancelOralMeasurement() {
        oralMeasurementFlowState = OralMeasurementFlowCoordinator.cancel(
            state: oralMeasurementFlowState
        )
    }

    private var currentRecoverableFailureStep: PairingStep? {
        guard case let .setup(pairingState) = route else {
            return nil
        }

        switch pairingState.step {
        case .claimFailed, .incompatible, .notReady, .timeout:
            return pairingState.step
        default:
            return nil
        }
    }

    private func recordPairingRecovery(step: PairingStep, action: PairingRecoveryAction) {
        pairingRecoveryEvents.append(
            PairingRecoveryEvent(
                failureStep: step.rawValue,
                recoveryAction: action.rawValue
            )
        )
    }

    private func currentFeatureContext() -> SelectedFeatureContext? {
        switch route {
        case .home:
            return nil
        case .setup:
            return nil
        case let .featureHub(context):
            return context
        case let .featureAction(context, _):
            return context
        }
    }

    private let nowEpochMillis: () -> Int64

    private func nextMeasurementSessionID(feature: FeatureKind) -> String {
        sampleSessionOrdinal += 1
        return "\(feature.rawValue)-session-\(sampleSessionOrdinal)"
    }

    private func entitlementBlockReason(
        for action: ManagedAction,
        entitlement: EffectiveEntitlement
    ) -> ActionLockReasonCode? {
        switch action {
        case .measure:
            return entitlement.canStartNewSessions ? nil : entitlement.reasonCode
        case .setGoals:
            return entitlement.canEditGoals ? nil : entitlement.reasonCode
        case .getSuggestion:
            return entitlement.canRequestLiveSuggestions ? nil : entitlement.reasonCode
        case .viewHistory, .consultProfessionals, .setup:
            return nil
        }
    }

    private func synthesizeCompletedSession(
        feature: FeatureKind,
        ordinal: Int64
    ) -> MeasurementSessionState {
        let sessionID = "\(feature.rawValue)-session-\(ordinal)"
        let resultToken = "\(String(feature.rawValue.prefix(4)))-result-\(ordinal)"

        return MeasurementSessionCoordinator.reduce(
            state: MeasurementSessionCoordinator.reduce(
                state: MeasurementSessionCoordinator.reduce(
                    state: MeasurementSessionState.begin(
                        sessionID: sessionID,
                        feature: feature
                    ),
                    event: .measurementStarted
                ),
                event: .terminalReadingAvailable(
                    MeasurementTerminalSummary(resultToken: resultToken)
                )
            ),
            event: .terminalReadingConfirmed
        )
    }
}

private extension EffectiveEntitlement {
    var reasonCode: ActionLockReasonCode {
        switch mode {
        case .active:
            return .conflictingActionInProgress
        case .temporaryAccess:
            return .temporaryAccessRestriction
        case .readOnly:
            return .readOnlyModeRestriction
        }
    }
}
