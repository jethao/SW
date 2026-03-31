import SwiftUI

enum FeatureKind: String, CaseIterable, Identifiable {
    case oralHealth = "oral_health"
    case fatBurning = "fat_burning"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .oralHealth:
            return "Oral Health"
        case .fatBurning:
            return "Fat Burning"
        }
    }

    var subtitle: String {
        switch self {
        case .oralHealth:
            return "Track oral-health trends over time."
        case .fatBurning:
            return "Follow repeated breath sessions and best-delta progress."
        }
    }
}

enum FeatureAction: String, CaseIterable, Identifiable {
    case setGoals = "set_goals"
    case viewHistory = "view_history"
    case measure = "measure"
    case getSuggestion = "get_suggestion"
    case consultProfessionals = "consult_professionals"

    var id: String { rawValue }

    var title: String {
        switch self {
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
}

struct SelectedFeatureContext {
    let feature: FeatureKind
    let lastVisitedRouteID: String
}

struct AppShellView: View {
    @StateObject private var store: AppShellStore
    @Environment(\.openURL) private var openURL

    init() {
        let store = AppShellStore()
        store.replaceEntitlementCacheState(
            scaffoldBootstrapEntitlementState(
                nowEpochMillis: Int64(Date().timeIntervalSince1970 * 1000)
            )
        )
        _store = StateObject(wrappedValue: store)
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle(title)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch store.route {
        case .home:
            FeatureHubHomeView(
                entitlement: store.effectiveEntitlement,
                onStartSetup: {
                    store.openSetup()
                },
                onSelectFeature: { feature in
                    store.openFeature(feature)
                }
            )
        case let .setup(pairingState):
            PairingSetupView(
                pairingState: pairingState,
                onAllowBluetooth: {
                    store.grantBluetoothPermission()
                },
                onDenyBluetooth: {
                    store.denyBluetoothPermission()
                },
                onDiscoverDevice: {
                    store.discoverDevice()
                },
                onDiscoveryTimeout: {
                    store.markDiscoveryTimeout()
                },
                onConnectDevice: {
                    store.connectDiscoveredDevice()
                },
                onShowIncompatible: {
                    store.markDeviceIncompatible()
                },
                onShowNotReady: {
                    store.markDeviceNotReady()
                },
                onStartClaim: {
                    store.startClaimDevice()
                },
                onClaimSuccess: {
                    store.completeClaimDevice()
                },
                onClaimFailure: {
                    store.failClaimDevice()
                },
                onRetryClaim: {
                    store.retryClaimDevice()
                },
                onRetryReadinessCheck: {
                    store.retryDeviceReadinessCheck()
                },
                onSelectMode: { mode in
                    store.selectSetupMode(mode)
                },
                onFinishConnection: {
                    store.confirmDeviceConnection()
                },
                onRetryPermission: {
                    store.retrySetupAfterFailure()
                },
                onRetryDiscovery: {
                    store.restartDiscovery()
                },
                onBackHome: {
                    store.exitSetup()
                }
            )
        case let .featureHub(context):
            FeatureActionsView(
                context: context,
                entitlement: store.effectiveEntitlement,
                currentGoal: store.goal(for: context.feature),
                currentSuggestion: store.suggestion(for: context.feature),
                blockedAttempt: store.lastBlockedActionAttempt,
                onSelectAction: { action in
                    store.openAction(action)
                },
                onReturnHome: {
                    store.returnHome()
                }
            )
        case let .featureAction(context, action):
            if action == .setGoals {
                FeatureGoalEditorView(
                    context: context,
                    entitlement: store.effectiveEntitlement,
                    currentGoal: store.goal(for: context.feature),
                    templates: goalTemplates(for: context.feature),
                    onApplyTemplate: { template in
                        store.applyGoalTemplate(template)
                    },
                    onClearGoal: {
                        store.clearGoal(feature: context.feature)
                    },
                    onReturnToFeature: {
                        store.returnToFeature()
                    },
                    onReturnHome: {
                        store.returnHome()
                    }
                )
            } else if action == .getSuggestion {
                FeatureSuggestionView(
                    context: context,
                    entitlement: store.effectiveEntitlement,
                    currentGoal: store.goal(for: context.feature),
                    currentSuggestion: store.suggestion(for: context.feature),
                    onRefreshFeatureSuggestion: {
                        store.refreshSuggestion(
                            feature: context.feature,
                            entryPoint: .featureHub
                        )
                    },
                    onRefreshResultSuggestion: {
                        store.refreshSuggestion(
                            feature: context.feature,
                            entryPoint: .resultContext
                        )
                    },
                    onReturnToFeature: {
                        store.returnToFeature()
                    },
                    onReturnHome: {
                        store.returnHome()
                    }
                )
            } else if action == .consultProfessionals {
                ConsultDirectoryView(
                    context: context,
                    entitlement: store.effectiveEntitlement,
                    directory: store.consultDirectory(for: context.feature),
                    pendingHandoff: store.pendingConsultHandoff,
                    latestHandoff: store.consultHandoffEvents.last(where: { $0.feature == context.feature.rawValue }),
                    onRefreshDirectory: {
                        store.refreshConsultDirectory(feature: context.feature)
                    },
                    onBeginHandoff: { resource in
                        store.beginConsultHandoff(resource: resource)
                    },
                    onConfirmHandoff: {
                        if let url = store.confirmConsultHandoff() {
                            openURL(url)
                        }
                    },
                    onCancelHandoff: {
                        store.cancelConsultHandoff()
                    },
                    onReturnToFeature: {
                        store.returnToFeature()
                    },
                    onReturnHome: {
                        store.returnHome()
                    }
                )
            } else if action == .viewHistory {
                FeatureHistoryView(
                    context: context,
                    entitlement: store.effectiveEntitlement,
                    oralSurface: store.sessionHistoryStoreState.oralHistorySurface(),
                    fatSurface: store.sessionHistoryStoreState.fatHistorySurface(),
                    syncProjection: store.syncQueueProjection(for: context.feature),
                    activeSyncJob: store.activeSyncJob(for: context.feature),
                    exportAuditSurface: store.exportAuditSurface(for: context.feature, platform: .appleHealth),
                    onRecordCompletedSummary: {
                        store.recordDemoCompletedSession(for: context.feature)
                    },
                    onStartNextSyncAttempt: {
                        _ = store.beginNextSyncAttempt()
                    },
                    onMarkSyncSuccess: {
                        if let activeJob = store.activeSyncJob(for: context.feature) {
                            store.markSyncAttemptSucceeded(sessionID: activeJob.sessionID)
                        }
                    },
                    onMarkSyncFailure: {
                        if let activeJob = store.activeSyncJob(for: context.feature) {
                            store.markSyncAttemptFailed(sessionID: activeJob.sessionID, reasonCode: "offline_retry_required")
                        }
                    },
                    onExportLatestSummary: {
                        _ = store.exportLatestCompletedSummary(
                            for: context.feature,
                            platform: .appleHealth
                        )
                    },
                    onFailLatestExport: {
                        store.failLatestCompletedSummaryExport(
                            for: context.feature,
                            platform: .appleHealth,
                            reasonCode: "apple_health_unavailable"
                        )
                    },
                    onGrantExportPermission: {
                        store.setExportPermission(.granted, for: .appleHealth)
                    },
                    onDenyExportPermission: {
                        store.setExportPermission(.denied, for: .appleHealth)
                    },
                    onReturnToFeature: {
                        store.returnToFeature()
                    },
                    onReturnHome: {
                        store.returnHome()
                    }
                )
            } else if action == .measure && context.feature == .oralHealth {
                OralMeasurementView(
                    context: context,
                    flowState: store.oralMeasurementFlowState,
                    recoveryState: store.measurementRecoveryState(for: context.feature),
                    onStartSession: {
                        store.startOralMeasurement()
                    },
                    onWarmupPassed: {
                        store.markOralWarmupPassed()
                    },
                    onWarmupFailed: {
                        store.markOralWarmupFailed()
                    },
                    onCaptureValidSample: {
                        store.completeOralMeasurement()
                    },
                    onInvalidSample: {
                        store.markOralInvalidSample()
                    },
                    onDisconnect: {
                        store.disconnectActiveMeasurement()
                    },
                    onCancelSession: {
                        store.cancelOralMeasurement()
                    },
                    onBeginReconnectReplay: {
                        store.beginReconnectReplay()
                    },
                    onRecoverReplay: {
                        store.recoverMeasurementReplay()
                    },
                    onFailReplay: {
                        store.failMeasurementReplay()
                    },
                    onReturnToFeature: {
                        store.returnToFeature()
                    },
                    onReturnHome: {
                        store.returnHome()
                    }
                )
            } else if action == .measure && context.feature == .fatBurning {
                FatMeasurementView(
                    context: context,
                    flowState: store.fatMeasurementFlowState,
                    recoveryState: store.measurementRecoveryState(for: context.feature),
                    onStartSession: {
                        store.startFatMeasurement()
                    },
                    onBaselineLocked: {
                        store.lockFatBaseline()
                    },
                    onRecordNextReading: { delta in
                        store.recordNextFatReading(deltaPercent: delta)
                    },
                    onRequestFinish: {
                        store.requestFatFinish()
                    },
                    onReceiveFinalSummary: { finalDelta in
                        store.completeFatMeasurement(finalDeltaPercent: finalDelta)
                    },
                    onFailSession: { reasonCode in
                        store.failFatMeasurement(reasonCode: reasonCode)
                    },
                    onDisconnect: {
                        store.disconnectActiveMeasurement()
                    },
                    onCancelSession: {
                        store.cancelFatMeasurement()
                    },
                    onBeginReconnectReplay: {
                        store.beginReconnectReplay()
                    },
                    onRecoverReplay: {
                        store.recoverMeasurementReplay()
                    },
                    onFailReplay: {
                        store.failMeasurementReplay()
                    },
                    onReturnToFeature: {
                        store.returnToFeature()
                    },
                    onReturnHome: {
                        store.returnHome()
                    }
                )
            } else {
                FeatureActionDestinationView(
                    context: context,
                    action: action,
                    entitlement: store.effectiveEntitlement,
                    activeAction: store.activeManagedAction,
                    blockedAttempt: store.lastBlockedActionAttempt,
                    onTryAction: { candidate in
                        store.openAction(candidate)
                    },
                    onReturnToFeature: {
                        store.returnToFeature()
                    },
                    onReturnHome: {
                        store.returnHome()
                    }
                )
            }
        }
    }

    private var title: String {
        switch store.route {
        case .home:
            return "AirHealth"
        case .setup:
            return "Add Device"
        case let .featureHub(context):
            return context.feature.title
        case let .featureAction(_, action):
            return action.title
        }
    }
}

private struct FeatureHubHomeView: View {
    let entitlement: EffectiveEntitlement
    let onStartSetup: () -> Void
    let onSelectFeature: (FeatureKind) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Choose a feature")
                    .font(.title2.bold())

                Text("Start from the home feature hub, keep the selected feature context, and route to the next action from there.")
                    .foregroundStyle(.secondary)

                if let banner = entitlementBannerState(entitlement) {
                    EntitlementBannerView(banner: banner)
                }

                Button("Add Device") {
                    onStartSetup()
                }
                .buttonStyle(.borderedProminent)

                Text("Start BLE setup, request Bluetooth access, discover nearby devices, and connect before claim/setup completion.")
                    .foregroundStyle(.secondary)

                ForEach(FeatureKind.allCases) { feature in
                    Button(action: { onSelectFeature(feature) }) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(feature.title)
                                .font(.headline)
                            Text(feature.subtitle)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text("Enter \(feature.title) flow")
                                .font(.footnote.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding()
        }
    }
}

private struct PairingSetupView: View {
    let pairingState: PairingFlowState
    let onAllowBluetooth: () -> Void
    let onDenyBluetooth: () -> Void
    let onDiscoverDevice: () -> Void
    let onDiscoveryTimeout: () -> Void
    let onConnectDevice: () -> Void
    let onShowIncompatible: () -> Void
    let onShowNotReady: () -> Void
    let onStartClaim: () -> Void
    let onClaimSuccess: () -> Void
    let onClaimFailure: () -> Void
    let onRetryClaim: () -> Void
    let onRetryReadinessCheck: () -> Void
    let onSelectMode: (SetupMode) -> Void
    let onFinishConnection: () -> Void
    let onRetryPermission: () -> Void
    let onRetryDiscovery: () -> Void
    let onBackHome: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("BLE setup")
                .font(.title3.bold())

            Text("Move from setup entry through Bluetooth permission, discovery, and connection before claim and mode setup.")
                .foregroundStyle(.secondary)

            Text("Current route ID: setup/\(pairingState.step.rawValue)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            switch pairingState.step {
            case .permissionPrimer:
                Text("AirHealth needs Bluetooth access to discover and connect to your device.")
                Button("Allow Bluetooth") {
                    onAllowBluetooth()
                }
                .buttonStyle(.borderedProminent)
                Button("Not Now") {
                    onDenyBluetooth()
                }
                .buttonStyle(.bordered)

            case .permissionDenied:
                Text(pairingState.recoveryMessage ?? "Bluetooth access is required.")
                    .foregroundStyle(.secondary)
                Button("Retry Permission") {
                    onRetryPermission()
                }
                .buttonStyle(.borderedProminent)
                Button("Back To Home") {
                    onBackHome()
                }
                .buttonStyle(.bordered)

            case .discovering:
                Text("Scanning for nearby AirHealth devices.")
                    .foregroundStyle(.secondary)
                Button("Use Discovered Device") {
                    onDiscoverDevice()
                }
                .buttonStyle(.borderedProminent)
                Button("No Device Found") {
                    onDiscoveryTimeout()
                }
                .buttonStyle(.bordered)

            case .deviceDiscovered:
                Text("AirHealth device discovered and ready to connect.")
                    .foregroundStyle(.secondary)
                if let device = pairingState.discoveredDevice {
                    Text("Device: \(device.name)")
                    Text("Protocol: \(device.protocolVersion) • Signal: \(device.signalLabel)")
                        .font(.footnote.monospaced())
                        .foregroundStyle(.secondary)
                }
                Button("Connect Device") {
                    onConnectDevice()
                }
                .buttonStyle(.borderedProminent)
                Button("Rescan") {
                    onRetryDiscovery()
                }
                .buttonStyle(.bordered)

            case .connecting:
                Text("Connecting over BLE and confirming protocol compatibility.")
                    .foregroundStyle(.secondary)
                if let device = pairingState.discoveredDevice {
                    Text("Connecting to \(device.name)")
                }
                Button("Finish Connection") {
                    onFinishConnection()
                }
                .buttonStyle(.borderedProminent)
                Button("Device Not Ready") {
                    onShowNotReady()
                }
                .buttonStyle(.bordered)
                Button("Unsupported Device") {
                    onShowIncompatible()
                }
                .buttonStyle(.bordered)

            case .incompatible:
                Text(pairingState.recoveryMessage ?? "This device is not supported.")
                    .foregroundStyle(.secondary)
                Button("Scan For Another Device") {
                    onRetryDiscovery()
                }
                .buttonStyle(.borderedProminent)
                Button("Back To Home") {
                    onBackHome()
                }
                .buttonStyle(.bordered)

            case .notReady:
                Text(pairingState.recoveryMessage ?? "This device is not ready for setup.")
                    .foregroundStyle(.secondary)
                Button("Retry Check") {
                    onRetryReadinessCheck()
                }
                .buttonStyle(.borderedProminent)
                Button("Back To Home") {
                    onBackHome()
                }
                .buttonStyle(.bordered)

            case .connected:
                Text("Device connected. Continue by claiming the device and choosing the initial feature mode.")
                    .foregroundStyle(.secondary)
                if let device = pairingState.discoveredDevice {
                    Text("Connected device: \(device.name)")
                    Text("Protocol: \(device.protocolVersion)")
                        .font(.footnote.monospaced())
                        .foregroundStyle(.secondary)
                }
                Button("Claim Device") {
                    onStartClaim()
                }
                .buttonStyle(.borderedProminent)

            case .claiming:
                Text("Claiming this device to the current AirHealth account.")
                    .foregroundStyle(.secondary)
                if let device = pairingState.discoveredDevice {
                    Text("Claim target: \(device.name)")
                }
                Button("Claim Succeeds") {
                    onClaimSuccess()
                }
                .buttonStyle(.borderedProminent)
                Button("Claim Fails") {
                    onClaimFailure()
                }
                .buttonStyle(.bordered)

            case .claimFailed:
                Text(pairingState.recoveryMessage ?? "Claim failed.")
                    .foregroundStyle(.secondary)
                Button("Retry Claim") {
                    onRetryClaim()
                }
                .buttonStyle(.borderedProminent)
                Button("Back To Home") {
                    onBackHome()
                }
                .buttonStyle(.bordered)

            case .modeSelection:
                Text("Device is now bound to \(pairingState.claimOwnerLabel ?? "your AirHealth account"). Choose the initial mode to finish setup.")
                    .foregroundStyle(.secondary)
                ForEach(SetupMode.allCases, id: \.rawValue) { mode in
                    Button("Use \(mode.title)") {
                        onSelectMode(mode)
                    }
                    .buttonStyle(.borderedProminent)
                }

            case .setupComplete:
                Text("Setup complete. The device is claimed and ready for downstream feature flows.")
                    .foregroundStyle(.secondary)
                if let device = pairingState.discoveredDevice {
                    Text("Connected device: \(device.name)")
                }
                Text("Owner binding: \(pairingState.claimOwnerLabel ?? "Primary AirHealth account")")
                Text("Initial mode: \(pairingState.selectedMode?.title ?? "Not selected")")
                Button("Back To Home") {
                    onBackHome()
                }
                .buttonStyle(.borderedProminent)

            case .timeout:
                Text(pairingState.recoveryMessage ?? "Discovery timed out.")
                    .foregroundStyle(.secondary)
                Button("Retry Scan") {
                    onRetryDiscovery()
                }
                .buttonStyle(.borderedProminent)
                Button("Back To Home") {
                    onBackHome()
                }
                .buttonStyle(.bordered)
            }

            Spacer()
        }
        .padding()
    }
}

private struct FeatureHistoryView: View {
    let context: SelectedFeatureContext
    let entitlement: EffectiveEntitlement
    let oralSurface: OralHistorySurfaceState
    let fatSurface: FatHistorySurfaceState
    let syncProjection: FeatureSyncQueueProjection
    let activeSyncJob: PersistedSessionSyncJob?
    let exportAuditSurface: ExportAuditSurfaceState
    let onRecordCompletedSummary: () -> Void
    let onStartNextSyncAttempt: () -> Void
    let onMarkSyncSuccess: () -> Void
    let onMarkSyncFailure: () -> Void
    let onExportLatestSummary: () -> Void
    let onFailLatestExport: () -> Void
    let onGrantExportPermission: () -> Void
    let onDenyExportPermission: () -> Void
    let onReturnToFeature: () -> Void
    let onReturnHome: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("History and progress")
                .font(.title3.bold())

            Text("Render consumer-safe history from persisted completed summaries, keep pending versus synced state explicit, and show feature-specific progress context.")
                .foregroundStyle(.secondary)

            Text("Selected feature: \(context.feature.title)")
            Text("Return route ID: \(context.lastVisitedRouteID)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            if let banner = entitlementBannerState(entitlement) {
                EntitlementBannerView(banner: banner)
            }

            if context.feature == .oralHealth {
                Text(oralSurface.baselineProgressLabel)
                    .font(.headline)
                Text(oralSurface.progressDetail)
                    .foregroundStyle(.secondary)
                if let latestStatusLabel = oralSurface.latestStatusLabel {
                    Text("Latest sync state: \(latestStatusLabel)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                ForEach(oralSurface.items, id: \.sessionID) { item in
                    Text(item.title)
                        .font(.subheadline.weight(.semibold))
                    Text("\(item.progressLabel) • \(item.syncLabel)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text(item.detail)
                        .foregroundStyle(.secondary)
                }
            } else {
                Text(fatSurface.latestFinalDeltaLabel ?? "No fat-burning sessions yet")
                    .font(.headline)
                if let bestDeltaLabel = fatSurface.bestDeltaLabel {
                    Text(bestDeltaLabel)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Text("\(fatSurface.pendingCount) pending • \(fatSurface.syncedCount) synced")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                ForEach(fatSurface.items, id: \.sessionID) { item in
                    Text(item.title)
                        .font(.subheadline.weight(.semibold))
                    Text("\(item.finalDeltaLabel) • \(item.bestDeltaLabel) • \(item.syncLabel)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text(item.detail)
                        .foregroundStyle(.secondary)
                }
            }

            Text("Sync queue")
                .font(.headline)
            Text("\(syncProjection.pendingCount) pending • \(syncProjection.retryScheduledCount) retry • \(syncProjection.inFlightCount) in flight • \(syncProjection.poisonedCount) poisoned • \(syncProjection.syncedCount) synced")
                .font(.footnote)
                .foregroundStyle(.secondary)
            if let nextJob = syncProjection.nextEligibleJob {
                Text("Next eligible sync: \(nextJob.sessionID)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
            }
            if let activeSyncJob {
                Text("Current sync attempt: \(activeSyncJob.sessionID)")
                    .foregroundStyle(.secondary)
                Text("Idempotency key: \(activeSyncJob.idempotencyKey)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
            }

            Button("Record Completed Summary") {
                onRecordCompletedSummary()
            }
            .buttonStyle(.borderedProminent)

            Button("Start Next Sync Attempt") {
                onStartNextSyncAttempt()
            }
            .buttonStyle(.bordered)
            .disabled(syncProjection.nextEligibleJob == nil)
            .opacity(syncProjection.nextEligibleJob == nil ? 0.58 : 1)

            if activeSyncJob != nil {
                Button("Mark Sync Success") {
                    onMarkSyncSuccess()
                }
                .buttonStyle(.borderedProminent)

                Button("Simulate Sync Failure") {
                    onMarkSyncFailure()
                }
                .buttonStyle(.bordered)
            }

            Text("Health export")
                .font(.headline)
            Text("Export the latest completed consumer-safe summary and keep an audit record for each success or failure attempt.")
                .foregroundStyle(.secondary)
            if let latestStatusLabel = exportAuditSurface.latestStatusLabel {
                Text("Latest export: \(latestStatusLabel) via \(exportAuditSurface.latestPlatformTitle ?? "health platform")")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if let latestFailureReason = exportAuditSurface.latestFailureReason {
                Text("Failure reason: \(latestFailureReason)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Text("Permission: \(exportAuditSurface.permissionState.rawValue)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)
            if exportAuditSurface.permissionState == .denied {
                Text("Apple Health permission is denied. Re-enable permission to retry export.")
                    .foregroundStyle(.secondary)
            }
            Button("Allow Apple Health") {
                onGrantExportPermission()
            }
            .buttonStyle(.bordered)
            Button("Deny Apple Health") {
                onDenyExportPermission()
            }
            .buttonStyle(.bordered)
            Button("Export To Apple Health") {
                onExportLatestSummary()
            }
            .buttonStyle(.borderedProminent)
            .disabled(exportAuditSurface.permissionState != .granted)
            .opacity(exportAuditSurface.permissionState == .granted ? 1 : 0.58)
            Button("Simulate Export Failure") {
                onFailLatestExport()
            }
            .buttonStyle(.bordered)
            ForEach(exportAuditSurface.records.prefix(3), id: \.auditID) { record in
                Text("\(record.platform.title): \(record.statusLabel)")
                    .font(.subheadline.weight(.semibold))
                Text("Session \(record.sessionID) • token \(record.exportedResultToken)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Button("Return To \(context.feature.title)") {
                onReturnToFeature()
            }
            .buttonStyle(.borderedProminent)

            Button("Return To Home") {
                onReturnHome()
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding()
    }
}

private struct FeatureActionsView: View {
    let context: SelectedFeatureContext
    let entitlement: EffectiveEntitlement
    let currentGoal: FeatureGoal?
    let currentSuggestion: FeatureSuggestion?
    let blockedAttempt: BlockedActionAttempt?
    let onSelectAction: (FeatureAction) -> Void
    let onReturnHome: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Selected feature context")
                .font(.headline)

            Text("\(context.feature.title) is active. Every child route inherits this feature context and can return here without losing it.")
                .foregroundStyle(.secondary)

            Text("Current route ID: feature_hub/\(context.feature.rawValue)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            Text("Selecting one action acquires the global action lock until that flow resolves.")
                .foregroundStyle(.secondary)

            if let banner = entitlementBannerState(entitlement) {
                EntitlementBannerView(banner: banner)
            }

            if let blockedAttempt {
                Text("Blocked action")
                    .font(.headline)
                Text(blockedAttempt.message)
                    .foregroundStyle(.secondary)
                Text("Reason code: \(blockedAttempt.reasonCode.rawValue)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
            }

            if let currentGoal {
                Text("Current goal")
                    .font(.headline)
                Text(currentGoal.summary)
                    .foregroundStyle(.secondary)
                Text("Target: \(currentGoal.targetLabel) • \(currentGoal.cadenceLabel)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Text("Local revision \(currentGoal.revision)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
            }

            if let currentSuggestion {
                Text("Cached suggestion")
                    .font(.headline)
                Text(currentSuggestion.headline)
                    .foregroundStyle(.secondary)
                Text(currentSuggestion.body)
                    .foregroundStyle(.secondary)
                Text("Source: \(currentSuggestion.entryPoint.rawValue) • Refresh \(currentSuggestion.refreshRevision)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
            }

            ForEach(FeatureAction.allCases) { action in
                let actionSurface = featureActionSurfaceState(action: action, entitlement: entitlement)
                Button(action.title) {
                    onSelectAction(action)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!actionSurface.isEnabled)
                .opacity(actionSurface.isEnabled ? 1 : 0.58)

                if let detail = actionSurface.detail {
                    Text(detail)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Button("Back To Home") {
                onReturnHome()
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding()
    }
}

private struct OralMeasurementView: View {
    let context: SelectedFeatureContext
    let flowState: OralMeasurementFlowState
    let recoveryState: MeasurementRecoveryState?
    let onStartSession: () -> Void
    let onWarmupPassed: () -> Void
    let onWarmupFailed: () -> Void
    let onCaptureValidSample: () -> Void
    let onInvalidSample: () -> Void
    let onDisconnect: () -> Void
    let onCancelSession: () -> Void
    let onBeginReconnectReplay: () -> Void
    let onRecoverReplay: () -> Void
    let onFailReplay: () -> Void
    let onReturnToFeature: () -> Void
    let onReturnHome: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Oral measurement")
                .font(.title3.bold())

            Text("Guide one oral session from warm-up through a confirmed score, and keep baseline-building progress explicit from 1/5 to 5/5.")
                .foregroundStyle(.secondary)

            Text("Selected feature: \(context.feature.title)")
            Text("Return route ID: \(context.lastVisitedRouteID)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            Text(flowState.baselineProgress.progressLabel)
                .font(.headline)
            Text(flowState.baselineProgress.detailLabel)
                .foregroundStyle(.secondary)
            if let recoveryState {
                RecoveryCardView(
                    recoveryState: recoveryState,
                    onBeginReconnectReplay: onBeginReconnectReplay,
                    onRecoverReplay: onRecoverReplay,
                    onFailReplay: onFailReplay
                )
            }

            switch flowState.step {
            case .preparing:
                Text("Start a guided oral session after brushing and with the device ready nearby.")
                    .foregroundStyle(.secondary)
                Button("Start Oral Session") {
                    onStartSession()
                }
                .buttonStyle(.borderedProminent)
            case .warming:
                Text("Warm-up is in progress. Keep the device stable before taking an oral sample.")
                    .foregroundStyle(.secondary)
                Button("Warm-up Passed") {
                    onWarmupPassed()
                }
                .buttonStyle(.borderedProminent)
                Button("Warm-up Failed") {
                    onWarmupFailed()
                }
                .buttonStyle(.bordered)
            case .measuring:
                Text("Take one steady oral sample. Invalid samples must stay retryable and never appear as completed results.")
                    .foregroundStyle(.secondary)
                Button("Capture Valid Oral Sample") {
                    onCaptureValidSample()
                }
                .buttonStyle(.borderedProminent)
                Button("Invalid Sample") {
                    onInvalidSample()
                }
                .buttonStyle(.bordered)
                Button("Simulate Disconnect") {
                    onDisconnect()
                }
                .buttonStyle(.bordered)
                Button("Cancel Session") {
                    onCancelSession()
                }
                .buttonStyle(.bordered)
            case .complete:
                Text(flowState.latestResult?.completionLabel ?? "Valid oral sample confirmed by the device.")
                    .foregroundStyle(.secondary)
                Text(flowState.latestResult?.scoreLabel ?? "Oral Health Score unavailable")
                    .font(.title3.bold())
                Text(flowState.latestResult?.baselineProgressLabel ?? flowState.baselineProgress.progressLabel)
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
                Text(flowState.latestResult?.baselineDetail ?? flowState.baselineProgress.detailLabel)
                    .foregroundStyle(.secondary)
                Button("Start Another Oral Session") {
                    onStartSession()
                }
                .buttonStyle(.borderedProminent)
            case .failed:
                Text(flowState.recoveryMessage ?? "This oral session did not complete. Retry without saving a result.")
                    .foregroundStyle(.secondary)
                Button("Retry Oral Session") {
                    onStartSession()
                }
                .buttonStyle(.borderedProminent)
            case .canceled:
                Text(flowState.recoveryMessage ?? "Session canceled. No oral score was saved.")
                    .foregroundStyle(.secondary)
                Button("Restart Oral Session") {
                    onStartSession()
                }
                .buttonStyle(.borderedProminent)
            }

            Button("Return To \(context.feature.title)") {
                onReturnToFeature()
            }
            .buttonStyle(.borderedProminent)

            Button("Return To Home") {
                onReturnHome()
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding()
    }
}

private struct FatMeasurementView: View {
    let context: SelectedFeatureContext
    let flowState: FatMeasurementFlowState
    let recoveryState: MeasurementRecoveryState?
    let onStartSession: () -> Void
    let onBaselineLocked: () -> Void
    let onRecordNextReading: (Int) -> Void
    let onRequestFinish: () -> Void
    let onReceiveFinalSummary: (Int) -> Void
    let onFailSession: (String) -> Void
    let onDisconnect: () -> Void
    let onCancelSession: () -> Void
    let onBeginReconnectReplay: () -> Void
    let onRecoverReplay: () -> Void
    let onFailReplay: () -> Void
    let onReturnToFeature: () -> Void
    let onReturnHome: () -> Void

    var body: some View {
        let nextSuggestedDelta = min((flowState.bestDeltaPercent ?? 0) + 4, 24)

        VStack(alignment: .leading, spacing: 16) {
            Text("Fat-burning measurement")
                .font(.title3.bold())

            Text("Guide a repeated-reading fat session with baseline lock, current delta updates, best-delta tracking, and a final summary that keeps latest and best values separate.")
                .foregroundStyle(.secondary)

            Text("Selected feature: \(context.feature.title)")
            Text("Return route ID: \(context.lastVisitedRouteID)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            Text("Target +\(flowState.targetDeltaPercent)%")
                .font(.headline)
            Text(
                flowState.baselineLocked
                    ? "\(flowState.readingCount) valid readings captured. Current delta \(flowState.currentDeltaPercent ?? 0)% • Best delta \(flowState.bestDeltaPercent ?? 0)%."
                    : "Coaching starts with breath, hold, and blow guidance before the first valid baseline reading locks at 0%."
            )
            .foregroundStyle(.secondary)
            if let recoveryState {
                RecoveryCardView(
                    recoveryState: recoveryState,
                    onBeginReconnectReplay: onBeginReconnectReplay,
                    onRecoverReplay: onRecoverReplay,
                    onFailReplay: onFailReplay
                )
            }

            switch flowState.step {
            case .preparing:
                Text("Start the repeated-reading coaching flow and keep the device steady for baseline lock.")
                    .foregroundStyle(.secondary)
                Button("Start Fat-Burning Session") {
                    onStartSession()
                }
                .buttonStyle(.borderedProminent)
            case .coaching:
                Text("Coach the user through breath, hold, and blow. The first valid reading locks the session baseline at 0%.")
                    .foregroundStyle(.secondary)
                Button("Baseline Locked") {
                    onBaselineLocked()
                }
                .buttonStyle(.borderedProminent)
                Button("Invalid Coaching Sample") {
                    onFailSession("invalid_sample")
                }
                .buttonStyle(.bordered)
            case .reading:
                Text("Track the current delta separately from the best delta. Later readings must not overwrite a stronger earlier delta.")
                    .foregroundStyle(.secondary)
                Text("Current delta: +\(flowState.currentDeltaPercent ?? 0)%")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
                Text("Best delta: +\(flowState.bestDeltaPercent ?? 0)%")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
                Button("Record Next Reading (+\(nextSuggestedDelta)%)") {
                    onRecordNextReading(nextSuggestedDelta)
                }
                .buttonStyle(.borderedProminent)
                Button("Finish Session") {
                    onRequestFinish()
                }
                .buttonStyle(.bordered)
                Button("Invalid Sample") {
                    onFailSession("invalid_sample")
                }
                .buttonStyle(.bordered)
                Button("Simulate Disconnect") {
                    onDisconnect()
                }
                .buttonStyle(.bordered)
                Button("Cancel Session") {
                    onCancelSession()
                }
                .buttonStyle(.bordered)
            case .finishPending:
                let bestDelta = flowState.bestDeltaPercent ?? 0
                let finalDelta = max(bestDelta - 3, 4)
                Text("Finish is requested. Wait for the device-generated final summary before showing a completed result.")
                    .foregroundStyle(.secondary)
                Text("Best delta so far: +\(bestDelta)%")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
                Button("Receive Final Summary (+\(finalDelta)%)") {
                    onReceiveFinalSummary(finalDelta)
                }
                .buttonStyle(.borderedProminent)
                Button("Disconnect Before Summary") {
                    onDisconnect()
                }
                .buttonStyle(.bordered)
            case .complete:
                Text(flowState.latestResult?.finalDeltaLabel ?? "Final Fat Burn Delta unavailable")
                    .font(.title3.bold())
                Text(flowState.latestResult?.bestDeltaLabel ?? "Best Fat Burn Delta unavailable")
                    .foregroundStyle(.secondary)
                Text(flowState.latestResult?.progressLabel ?? "\(flowState.readingCount) valid readings")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
                Text(flowState.latestResult?.goalStatusLabel ?? "Session summary ready.")
                    .foregroundStyle(.secondary)
                Button("Start Another Fat Session") {
                    onStartSession()
                }
                .buttonStyle(.borderedProminent)
            case .failed:
                Text(flowState.recoveryMessage ?? "This fat-burning session did not complete. Retry without saving a result.")
                    .foregroundStyle(.secondary)
                Button("Retry Fat Session") {
                    onStartSession()
                }
                .buttonStyle(.borderedProminent)
            case .canceled:
                Text(flowState.recoveryMessage ?? "Session canceled. No fat-burning summary was saved.")
                    .foregroundStyle(.secondary)
                Button("Restart Fat Session") {
                    onStartSession()
                }
                .buttonStyle(.borderedProminent)
            }

            Button("Return To \(context.feature.title)") {
                onReturnToFeature()
            }
            .buttonStyle(.borderedProminent)

            Button("Return To Home") {
                onReturnHome()
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding()
    }
}

private struct RecoveryCardView: View {
    let recoveryState: MeasurementRecoveryState
    let onBeginReconnectReplay: () -> Void
    let onRecoverReplay: () -> Void
    let onFailReplay: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Recovery status")
                .font(.headline)
            Text(recoveryState.statusMessage)
                .foregroundStyle(.secondary)
            Text("Recovery stage: \(recoveryStageLabel) • Session \(recoveryState.sessionID)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)
            if let recoveredResultToken = recoveryState.recoveredResultToken {
                Text("Recovered token: \(recoveredResultToken)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
            }
            switch recoveryState.stage {
            case .interrupted:
                Button("Reconnect And Query Session") {
                    onBeginReconnectReplay()
                }
                .buttonStyle(.borderedProminent)
            case .reconnecting:
                Button("Replay Completed Result") {
                    onRecoverReplay()
                }
                .buttonStyle(.borderedProminent)
                Button("Replay Failed") {
                    onFailReplay()
                }
                .buttonStyle(.bordered)
            case .recovered, .failed:
                EmptyView()
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var recoveryStageLabel: String {
        switch recoveryState.stage {
        case .interrupted:
            return "interrupted"
        case .reconnecting:
            return "reconnecting"
        case .recovered:
            return "recovered"
        case .failed:
            return "failed"
        }
    }
}

private struct FeatureSuggestionView: View {
    let context: SelectedFeatureContext
    let entitlement: EffectiveEntitlement
    let currentGoal: FeatureGoal?
    let currentSuggestion: FeatureSuggestion?
    let onRefreshFeatureSuggestion: () -> Void
    let onRefreshResultSuggestion: () -> Void
    let onReturnToFeature: () -> Void
    let onReturnHome: () -> Void

    var body: some View {
        let suggestionSurface = suggestionRefreshSurfaceState(
            entitlement: entitlement,
            hasCachedSuggestion: currentSuggestion != nil
        )

        VStack(alignment: .leading, spacing: 16) {
            Text("Suggestion planner")
                .font(.title3.bold())

            Text("Refresh a feature-specific suggestion, cache it locally for reopen, and keep it tied to the current feature context.")
                .foregroundStyle(.secondary)

            Text("Selected feature: \(context.feature.title)")
            Text("Return route ID: \(context.lastVisitedRouteID)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            if let banner = entitlementBannerState(entitlement) {
                EntitlementBannerView(banner: banner)
            }

            if let currentGoal {
                Text("Goal context")
                    .font(.headline)
                Text(currentGoal.summary)
                    .foregroundStyle(.secondary)
                Text("Target: \(currentGoal.targetLabel) • \(currentGoal.cadenceLabel)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            if let currentSuggestion {
                Text("Cached suggestion")
                    .font(.headline)
                Text(currentSuggestion.headline)
                    .foregroundStyle(.secondary)
                Text(currentSuggestion.body)
                    .foregroundStyle(.secondary)
                Text("Support track: \(currentSuggestion.supportingActionLabel)")
                Text("Source: \(currentSuggestion.entryPoint.rawValue) • Refresh \(currentSuggestion.refreshRevision)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
            } else {
                Text(
                    "No cached suggestion yet for \(context.feature.title). " +
                    (suggestionSurface.canRefreshSuggestion
                        ? "Refresh one now and it will stay available when you re-enter this feature."
                        : "A cached suggestion will appear here once active entitlement allows the first refresh.")
                )
                    .foregroundStyle(.secondary)
            }

            if let detail = suggestionSurface.detail {
                Text(detail)
                    .foregroundStyle(.secondary)
            }

            Button("Refresh From Feature Hub") {
                onRefreshFeatureSuggestion()
            }
            .buttonStyle(.borderedProminent)
            .disabled(!suggestionSurface.canRefreshSuggestion)
            .opacity(suggestionSurface.canRefreshSuggestion ? 1 : 0.58)

            Button("Refresh Result Follow-up") {
                onRefreshResultSuggestion()
            }
            .buttonStyle(.bordered)
            .disabled(!suggestionSurface.canRefreshSuggestion)
            .opacity(suggestionSurface.canRefreshSuggestion ? 1 : 0.58)

            Button("Return To \(context.feature.title)") {
                onReturnToFeature()
            }
            .buttonStyle(.borderedProminent)

            Button("Return To Home") {
                onReturnHome()
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding()
    }
}

private struct ConsultDirectoryView: View {
    let context: SelectedFeatureContext
    let entitlement: EffectiveEntitlement
    let directory: FeatureConsultDirectory?
    let pendingHandoff: PendingConsultHandoff?
    let latestHandoff: ConsultHandoffEvent?
    let onRefreshDirectory: () -> Void
    let onBeginHandoff: (ConsultDirectoryResource) -> Void
    let onConfirmHandoff: () -> Void
    let onCancelHandoff: () -> Void
    let onReturnToFeature: () -> Void
    let onReturnHome: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Consult professionals")
                .font(.title3.bold())

            Text("Load and cache a feature-scoped support directory so consult resources stay available when the user reopens this feature.")
                .foregroundStyle(.secondary)

            Text("Selected feature: \(context.feature.title)")
            Text("Return route ID: \(context.lastVisitedRouteID)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            if let banner = entitlementBannerState(entitlement) {
                EntitlementBannerView(banner: banner)
            }

            if let latestHandoff {
                Text("Latest outbound handoff")
                    .font(.headline)
                Text("\(latestHandoff.resourceTitle) launched via \(latestHandoff.launchLabel).")
                Text("Destination: \(latestHandoff.targetHost)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
            }

            if let directory {
                Text("Cached directory")
                    .font(.headline)
                Text("Locale \(directory.localeTag) • revision \(directory.refreshRevision)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)

                ForEach(directory.resources) { resource in
                    Text(resource.title)
                        .font(.headline)
                    Text("\(resource.specialtyLabel) • \(resource.regionLabel) • \(resource.availabilityLabel)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text(resource.detail)
                        .foregroundStyle(.secondary)
                    Text(resource.handoffHint)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    if pendingHandoff?.resource.id == resource.id {
                        Text("Leave AirHealth")
                            .font(.headline)
                        Text(resource.leaveAirHealthMessage)
                            .foregroundStyle(.secondary)
                        Text("Destination: \(resource.externalURL.absoluteString)")
                            .font(.footnote.monospaced())
                            .foregroundStyle(.secondary)
                        Button(resource.launchLabel) {
                            onConfirmHandoff()
                        }
                        .buttonStyle(.borderedProminent)
                        Button("Stay In AirHealth") {
                            onCancelHandoff()
                        }
                        .buttonStyle(.bordered)
                    } else {
                        Button(resource.launchLabel) {
                            onBeginHandoff(resource)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
            } else {
                Text("No consult directory cached yet for \(context.feature.title). Load the localized support list to keep it available offline for later reopen.")
                    .foregroundStyle(.secondary)
            }

            Button(directory == nil ? "Load Support Directory" : "Refresh Support Directory") {
                onRefreshDirectory()
            }
            .buttonStyle(.borderedProminent)

            Button("Return To \(context.feature.title)") {
                onReturnToFeature()
            }
            .buttonStyle(.borderedProminent)

            Button("Return To Home") {
                onReturnHome()
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding()
    }
}

private struct FeatureGoalEditorView: View {
    let context: SelectedFeatureContext
    let entitlement: EffectiveEntitlement
    let currentGoal: FeatureGoal?
    let templates: [GoalDraftTemplate]
    let onApplyTemplate: (GoalDraftTemplate) -> Void
    let onClearGoal: () -> Void
    let onReturnToFeature: () -> Void
    let onReturnHome: () -> Void

    var body: some View {
        let goalSurface = goalEditorSurfaceState(entitlement: entitlement)

        VStack(alignment: .leading, spacing: 16) {
            Text("Goal planner")
                .font(.title3.bold())

            Text("Create or revise a feature-specific goal and keep it cached locally so the same selection is available when this feature is reopened.")
                .foregroundStyle(.secondary)

            Text("Selected feature: \(context.feature.title)")
            Text("Return route ID: \(context.lastVisitedRouteID)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            if let banner = entitlementBannerState(entitlement) {
                EntitlementBannerView(banner: banner)
            }

            if let detail = goalSurface.detail {
                Text(detail)
                    .foregroundStyle(.secondary)
            }

            if let currentGoal {
                Text("Current cached goal")
                    .font(.headline)
                Text(currentGoal.summary)
                    .foregroundStyle(.secondary)
                Text("Target: \(currentGoal.targetLabel)")
                Text("Cadence: \(currentGoal.cadenceLabel)")
                Text("Local revision \(currentGoal.revision)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
                Button("Clear Goal") {
                    onClearGoal()
                }
                .buttonStyle(.bordered)
                .disabled(!goalSurface.canEditGoal)
                .opacity(goalSurface.canEditGoal ? 1 : 0.58)
            } else {
                Text(
                    "No cached goal yet for \(context.feature.title). " +
                    (goalSurface.canEditGoal
                        ? "Choose a draft below to create one locally."
                        : "Goal editing is paused until active entitlement returns.")
                )
                    .foregroundStyle(.secondary)
            }

            Text("Goal drafts")
                .font(.headline)

            ForEach(templates) { template in
                Button(template.title) {
                    onApplyTemplate(template)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!goalSurface.canEditGoal)
                .opacity(goalSurface.canEditGoal ? 1 : 0.58)

                Text(template.summary)
                    .foregroundStyle(.secondary)
                Text("Target: \(template.targetLabel) • \(template.cadenceLabel)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Button("Return To \(context.feature.title)") {
                onReturnToFeature()
            }
            .buttonStyle(.borderedProminent)

            Button("Return To Home") {
                onReturnHome()
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding()
    }
}

private struct FeatureActionDestinationView: View {
    let context: SelectedFeatureContext
    let action: FeatureAction
    let entitlement: EffectiveEntitlement
    let activeAction: ManagedAction?
    let blockedAttempt: BlockedActionAttempt?
    let onTryAction: (FeatureAction) -> Void
    let onReturnToFeature: () -> Void
    let onReturnHome: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(action.title)
                .font(.title3.bold())

            Text("This child route inherits the \(context.feature.title) context and preserves return-to-feature behavior.")
                .foregroundStyle(.secondary)

            Text("Active action lock: \(activeAction?.title ?? action.title)")
                .font(.headline)

            Text("Selected feature: \(context.feature.title)")
            Text("Return route ID: \(context.lastVisitedRouteID)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            if let banner = entitlementBannerState(entitlement) {
                EntitlementBannerView(banner: banner)
            }

            if let blockedAttempt {
                Text("Blocked action")
                    .font(.headline)
                Text(blockedAttempt.message)
                    .foregroundStyle(.secondary)
                Text("Reason code: \(blockedAttempt.reasonCode.rawValue)")
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
            }

            Text("Other entry points remain locked")
                .font(.headline)

            ForEach(FeatureAction.allCases) { candidate in
                if candidate == action {
                    Text("Current flow: \(candidate.title)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    let actionSurface = featureActionSurfaceState(action: candidate, entitlement: entitlement)
                    Button("Try \(candidate.title)") {
                        onTryAction(candidate)
                    }
                    .buttonStyle(.bordered)
                    .disabled(!actionSurface.isEnabled)
                    .opacity(actionSurface.isEnabled ? 1 : 0.58)

                    if let detail = actionSurface.detail {
                        Text(detail)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Button("Return To \(context.feature.title)") {
                onReturnToFeature()
            }
            .buttonStyle(.borderedProminent)

            Button("Return To Home") {
                onReturnHome()
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding()
    }
}

private struct EntitlementBannerState {
    let title: String
    let message: String
}

private struct FeatureActionSurfaceState {
    let isEnabled: Bool
    let detail: String?
}

private struct GoalEditorSurfaceState {
    let canEditGoal: Bool
    let detail: String?
}

private struct SuggestionRefreshSurfaceState {
    let canRefreshSuggestion: Bool
    let detail: String?
}

private struct EntitlementBannerView: View {
    let banner: EntitlementBannerState

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(banner.title)
                .font(.headline)
            Text(banner.message)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private func entitlementBannerState(_ entitlement: EffectiveEntitlement) -> EntitlementBannerState? {
    switch entitlement.mode {
    case .active:
        return nil
    case .temporaryAccess:
        return EntitlementBannerState(
            title: "Temporary access",
            message: "Verification is temporarily unavailable. You can still view history and consult resources, but new sessions, goal edits, and live suggestions stay paused until verification recovers."
        )
    case .readOnly:
        let message: String
        switch entitlement.freshness {
        case .staleCache:
            message = "Your last verified access is stale. History and consult resources remain available, but measurement, goal changes, and live suggestions stay locked until entitlement is verified again."
        case .inactiveCache, .verified, .missingCache, .freshCache:
            message = "Your access is currently read-only. History and consult resources remain available, but measurement, goal changes, and live suggestions stay locked until active entitlement returns."
        }

        return EntitlementBannerState(
            title: "Read-only mode",
            message: message
        )
    }
}

private func featureActionSurfaceState(
    action: FeatureAction,
    entitlement: EffectiveEntitlement
) -> FeatureActionSurfaceState {
    if entitlement.mode == .active {
        return FeatureActionSurfaceState(isEnabled: true, detail: nil)
    }

    let isEnabled: Bool
    switch action {
    case .measure:
        isEnabled = entitlement.canStartNewSessions
    case .setGoals:
        isEnabled = entitlement.canEditGoals
    case .getSuggestion:
        isEnabled = entitlement.canRequestLiveSuggestions
    case .viewHistory, .consultProfessionals:
        isEnabled = true
    }

    guard !isEnabled else {
        return FeatureActionSurfaceState(isEnabled: true, detail: nil)
    }

    let detail: String
    switch entitlement.mode {
    case .active:
        detail = ""
    case .temporaryAccess:
        detail = "\(action.title) is unavailable during Temporary access. Verify entitlement again to continue."
    case .readOnly:
        detail = "\(action.title) is unavailable in Read-only mode. Restore active entitlement to continue."
    }

    return FeatureActionSurfaceState(
        isEnabled: false,
        detail: detail
    )
}

private func goalEditorSurfaceState(
    entitlement: EffectiveEntitlement
) -> GoalEditorSurfaceState {
    let actionSurface = featureActionSurfaceState(
        action: .setGoals,
        entitlement: entitlement
    )
    return GoalEditorSurfaceState(
        canEditGoal: actionSurface.isEnabled,
        detail: actionSurface.detail
    )
}

private func suggestionRefreshSurfaceState(
    entitlement: EffectiveEntitlement,
    hasCachedSuggestion: Bool
) -> SuggestionRefreshSurfaceState {
    let actionSurface = featureActionSurfaceState(
        action: .getSuggestion,
        entitlement: entitlement
    )

    if actionSurface.isEnabled {
        return SuggestionRefreshSurfaceState(
            canRefreshSuggestion: true,
            detail: nil
        )
    }

    let detail: String?
    switch (entitlement.mode, hasCachedSuggestion) {
    case (.active, _):
        detail = nil
    case (.temporaryAccess, true):
        detail = "Live suggestion refresh is paused during Temporary access. Your cached suggestion stays available until verification recovers."
    case (.temporaryAccess, false):
        detail = "No cached suggestion is available yet. Live suggestion refresh is paused during Temporary access."
    case (.readOnly, true):
        detail = "Live suggestion refresh is unavailable in Read-only mode. Your cached suggestion stays available until active entitlement returns."
    case (.readOnly, false):
        detail = "No cached suggestion is available yet. Live suggestion refresh is unavailable in Read-only mode."
    }

    return SuggestionRefreshSurfaceState(
        canRefreshSuggestion: false,
        detail: detail ?? actionSurface.detail
    )
}
