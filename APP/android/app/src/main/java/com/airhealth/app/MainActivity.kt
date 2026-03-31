package com.airhealth.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private val routeState = FeatureHubRouteState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeState.replaceEntitlementCacheState(
            scaffoldBootstrapEntitlementState(System.currentTimeMillis()),
        )
        renderRoute()
    }

    private fun renderRoute() {
        val container = ScrollView(this).apply {
            addView(buildContent())
        }

        setContentView(container)
    }

    private fun buildContent(): View {
        return when (val route = routeState.route) {
            FeatureHubRoute.Home -> buildHomeView()
            is FeatureHubRoute.Setup -> buildSetupView(route.pairingState)
            is FeatureHubRoute.Feature -> buildFeatureView(route.context)
            is FeatureHubRoute.Action -> buildActionView(route.context, route.action)
        }
    }

    private fun buildHomeView(): View {
        title = "AirHealth"
        val entitlement = routeState.effectiveEntitlement

        return verticalLayout().apply {
            addView(
                headline("Choose a feature")
            )
            addView(
                bodyCopy(
                    "Start from the home feature hub, keep the selected feature context, and route to the next action from there."
                )
            )
            entitlementBannerState(entitlement)?.let { banner ->
                addEntitlementBanner(banner)
            }
            addView(
                actionButton("Add Device") {
                    routeState.openSetup()
                    renderRoute()
                }
            )
            addView(
                bodyCopy("Start BLE setup, request Bluetooth access, discover nearby devices, and connect before claim/setup completion.")
            )

            FeatureKind.entries.forEach { feature ->
                addView(
                    actionButton("Enter ${feature.title} flow") {
                        routeState.openFeature(feature)
                        renderRoute()
                    }
                )
                addView(bodyCopy(feature.subtitle))
            }
        }
    }

    private fun buildSetupView(pairingState: PairingFlowState): View {
        title = "Add Device"

        return verticalLayout().apply {
            addView(headline("BLE setup"))
            addView(
                bodyCopy(
                    "Move from setup entry through Bluetooth permission, discovery, and connection before claim and mode setup."
                )
            )
            addView(caption("Current route ID: setup/${pairingState.step.routeId}"))

            when (pairingState.step) {
                PairingStep.PERMISSION_PRIMER -> {
                    addView(bodyCopy("AirHealth needs Bluetooth access to discover and connect to your device."))
                    addView(
                        actionButton("Allow Bluetooth") {
                            routeState.grantBluetoothPermission()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("Not Now") {
                            routeState.denyBluetoothPermission()
                            renderRoute()
                        }
                    )
                }

                PairingStep.PERMISSION_DENIED -> {
                    addView(bodyCopy(pairingState.recoveryMessage ?: "Bluetooth access is required."))
                    addView(
                        actionButton("Retry Permission") {
                            routeState.retrySetupAfterFailure()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("Back To Home") {
                            routeState.exitSetup()
                            renderRoute()
                        }
                    )
                }

                PairingStep.DISCOVERING -> {
                    addView(bodyCopy("Scanning for nearby AirHealth devices."))
                    addView(
                        actionButton("Use Discovered Device") {
                            routeState.discoverDevice()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("No Device Found") {
                            routeState.markDiscoveryTimeout()
                            renderRoute()
                        }
                    )
                }

                PairingStep.DEVICE_DISCOVERED -> {
                    val device = pairingState.discoveredDevice
                    addView(bodyCopy("AirHealth device discovered and ready to connect."))
                    if (device != null) {
                        addView(bodyCopy("Device: ${device.name}"))
                        addView(caption("Protocol: ${device.protocolVersion} • Signal: ${device.signalLabel}"))
                    }
                    addView(
                        actionButton("Connect Device") {
                            routeState.connectDiscoveredDevice()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("Rescan") {
                            routeState.restartDiscovery()
                            renderRoute()
                        }
                    )
                }

                PairingStep.CONNECTING -> {
                    val device = pairingState.discoveredDevice
                    addView(bodyCopy("Connecting over BLE and confirming protocol compatibility."))
                    if (device != null) {
                        addView(bodyCopy("Connecting to ${device.name}"))
                    }
                    addView(
                        actionButton("Finish Connection") {
                            routeState.confirmDeviceConnection()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("Device Not Ready") {
                            routeState.markDeviceNotReady()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("Unsupported Device") {
                            routeState.markDeviceIncompatible()
                            renderRoute()
                        }
                    )
                }

                PairingStep.INCOMPATIBLE -> {
                    addView(bodyCopy(pairingState.recoveryMessage ?: "This device is not supported."))
                    addView(
                        actionButton("Scan For Another Device") {
                            routeState.restartDiscovery()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("Back To Home") {
                            routeState.exitSetup()
                            renderRoute()
                        }
                    )
                }

                PairingStep.NOT_READY -> {
                    addView(bodyCopy(pairingState.recoveryMessage ?: "This device is not ready for setup."))
                    addView(
                        actionButton("Retry Check") {
                            routeState.retryDeviceReadinessCheck()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("Back To Home") {
                            routeState.exitSetup()
                            renderRoute()
                        }
                    )
                }

                PairingStep.CONNECTED -> {
                    val device = pairingState.discoveredDevice
                    addView(bodyCopy("Device connected. Continue by claiming the device and choosing the initial feature mode."))
                    if (device != null) {
                        addView(bodyCopy("Connected device: ${device.name}"))
                        addView(caption("Protocol: ${device.protocolVersion}"))
                    }
                    addView(
                        actionButton("Claim Device") {
                            routeState.startClaimDevice()
                            renderRoute()
                        }
                    )
                }

                PairingStep.CLAIMING -> {
                    val device = pairingState.discoveredDevice
                    addView(bodyCopy("Claiming this device to the current AirHealth account."))
                    if (device != null) {
                        addView(bodyCopy("Claim target: ${device.name}"))
                    }
                    addView(
                        actionButton("Claim Succeeds") {
                            routeState.completeClaimDevice()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("Claim Fails") {
                            routeState.failClaimDevice()
                            renderRoute()
                        }
                    )
                }

                PairingStep.CLAIM_FAILED -> {
                    addView(bodyCopy(pairingState.recoveryMessage ?: "Claim failed."))
                    addView(
                        actionButton("Retry Claim") {
                            routeState.retryClaimDevice()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("Back To Home") {
                            routeState.exitSetup()
                            renderRoute()
                        }
                    )
                }

                PairingStep.MODE_SELECTION -> {
                    addView(bodyCopy("Device is now bound to ${pairingState.claimOwnerLabel ?: "your AirHealth account"}. Choose the initial mode to finish setup."))
                    SetupMode.entries.forEach { mode ->
                        addView(
                            actionButton("Use ${mode.title}") {
                                routeState.selectSetupMode(mode)
                                renderRoute()
                            }
                        )
                    }
                }

                PairingStep.SETUP_COMPLETE -> {
                    val device = pairingState.discoveredDevice
                    addView(bodyCopy("Setup complete. The device is claimed and ready for downstream feature flows."))
                    if (device != null) {
                        addView(bodyCopy("Connected device: ${device.name}"))
                    }
                    addView(bodyCopy("Owner binding: ${pairingState.claimOwnerLabel ?: "Primary AirHealth account"}"))
                    addView(bodyCopy("Initial mode: ${pairingState.selectedMode?.title ?: "Not selected"}"))
                    addView(
                        actionButton("Back To Home") {
                            routeState.exitSetup()
                            renderRoute()
                        }
                    )
                }

                PairingStep.TIMEOUT -> {
                    addView(bodyCopy(pairingState.recoveryMessage ?: "Discovery timed out."))
                    addView(
                        actionButton("Retry Scan") {
                            routeState.restartDiscovery()
                            renderRoute()
                        }
                    )
                    addView(
                        secondaryButton("Back To Home") {
                            routeState.exitSetup()
                            renderRoute()
                        }
                    )
                }
            }
        }
    }

    private fun buildFeatureView(context: SelectedFeatureContext): View {
        title = context.feature.title
        val entitlement = routeState.effectiveEntitlement

        return verticalLayout().apply {
            addView(headline("Selected feature context"))
            addView(
                bodyCopy(
                    "${context.feature.title} is active. Every child route inherits this feature context and can return here without losing it."
                )
            )
            addView(
                caption("Current route ID: feature_hub/${context.feature.routeId}")
            )
            addView(
                bodyCopy("Selecting one action acquires the global action lock until that flow resolves.")
            )
            entitlementBannerState(entitlement)?.let { banner ->
                addEntitlementBanner(banner)
            }

            routeState.lastBlockedActionAttempt?.let { blockedAttempt ->
                addView(headline("Blocked action"))
                addView(bodyCopy(blockedAttempt.message))
                addView(caption("Reason code: ${blockedAttempt.reasonCode.code}"))
            }

            routeState.goalFor(context.feature)?.let { goal ->
                addView(headline("Current goal"))
                addView(bodyCopy(goal.summary))
                addView(caption("Target: ${goal.targetLabel} • ${goal.cadenceLabel}"))
                addView(caption("Local revision ${goal.revision}"))
            }

            routeState.suggestionFor(context.feature)?.let { suggestion ->
                addView(headline("Cached suggestion"))
                addView(bodyCopy(suggestion.headline))
                addView(bodyCopy(suggestion.body))
                addView(caption("Source: ${suggestion.entryPoint.wireValue} • Refresh ${suggestion.refreshRevision}"))
            }

            FeatureAction.entries.forEach { action ->
                val actionSurface = featureActionSurfaceState(action, entitlement)
                addView(
                    actionButton(
                        action.title,
                        enabled = actionSurface.isEnabled,
                    ) {
                        routeState.openAction(action)
                        renderRoute()
                    }
                )
                actionSurface.detail?.let { detail ->
                    addView(caption(detail))
                }
            }

            addView(
                secondaryButton("Back To Home") {
                    routeState.returnHome()
                    renderRoute()
                }
            )
        }
    }

    private fun buildActionView(
        context: SelectedFeatureContext,
        action: FeatureAction,
    ): View {
        if (action == FeatureAction.SET_GOALS) {
            return buildGoalActionView(context)
        }
        if (action == FeatureAction.GET_SUGGESTION) {
            return buildSuggestionActionView(context)
        }
        if (action == FeatureAction.VIEW_HISTORY) {
            return buildHistoryActionView(context)
        }
        if (action == FeatureAction.MEASURE && context.feature == FeatureKind.ORAL_HEALTH) {
            return buildOralMeasurementActionView(context)
        }
        if (action == FeatureAction.MEASURE && context.feature == FeatureKind.FAT_BURNING) {
            return buildFatMeasurementActionView(context)
        }

        title = action.title
        val entitlement = routeState.effectiveEntitlement

        return verticalLayout().apply {
            addView(headline(action.title))
            addView(
                bodyCopy(
                    "This child route inherits the ${context.feature.title} context and preserves return-to-feature behavior."
                )
            )
            addView(
                bodyCopy(
                    "Active action lock: ${routeState.activeManagedAction()?.title ?: action.title}"
                )
            )
            addView(bodyCopy("Selected feature: ${context.feature.title}"))
            addView(caption("Return route ID: ${context.lastVisitedRouteId}"))
            entitlementBannerState(entitlement)?.let { banner ->
                addEntitlementBanner(banner)
            }

            routeState.lastBlockedActionAttempt?.let { blockedAttempt ->
                addView(
                    headline("Blocked action")
                )
                addView(bodyCopy(blockedAttempt.message))
                addView(caption("Reason code: ${blockedAttempt.reasonCode.code}"))
            }

            addView(headline("Other entry points remain locked"))
            FeatureAction.entries.forEach { candidate ->
                if (candidate == action) {
                    addView(caption("Current flow: ${candidate.title}"))
                } else {
                    val actionSurface = featureActionSurfaceState(candidate, entitlement)
                    addView(
                        secondaryButton(
                            "Try ${candidate.title}",
                            enabled = actionSurface.isEnabled,
                        ) {
                            routeState.openAction(candidate)
                            renderRoute()
                        }
                    )
                    actionSurface.detail?.let { detail ->
                        addView(caption(detail))
                    }
                }
            }

            addView(
                actionButton("Return To ${context.feature.title}") {
                    routeState.returnToFeature()
                    renderRoute()
                }
            )
            addView(
                secondaryButton("Return To Home") {
                    routeState.returnHome()
                    renderRoute()
                }
            )
        }
    }

    private fun buildOralMeasurementActionView(context: SelectedFeatureContext): View {
        title = FeatureAction.MEASURE.title
        val flowState = routeState.oralMeasurementFlowState

        return verticalLayout().apply {
            addView(headline("Oral measurement"))
            addView(
                bodyCopy(
                    "Guide one oral session from warm-up through a confirmed score, and keep baseline-building progress explicit from 1/5 to 5/5.",
                ),
            )
            addView(bodyCopy("Selected feature: ${context.feature.title}"))
            addView(caption("Return route ID: ${context.lastVisitedRouteId}"))
            addView(headline(flowState.baselineProgress.progressLabel))
            addView(bodyCopy(flowState.baselineProgress.detailLabel))

            when (flowState.step) {
                OralMeasurementFlowStep.PREPARING -> {
                    addView(bodyCopy("Start a guided oral session after brushing and with the device ready nearby."))
                    addView(
                        actionButton("Start Oral Session") {
                            routeState.startOralMeasurement()
                            renderRoute()
                        },
                    )
                }

                OralMeasurementFlowStep.WARMING -> {
                    addView(bodyCopy("Warm-up is in progress. Keep the device stable before taking an oral sample."))
                    addView(
                        actionButton("Warm-up Passed") {
                            routeState.markOralWarmupPassed()
                            renderRoute()
                        },
                    )
                    addView(
                        secondaryButton("Warm-up Failed") {
                            routeState.markOralWarmupFailed()
                            renderRoute()
                        },
                    )
                }

                OralMeasurementFlowStep.MEASURING -> {
                    addView(bodyCopy("Take one steady oral sample. Invalid samples must stay retryable and never appear as completed results."))
                    addView(
                        actionButton("Capture Valid Oral Sample") {
                            routeState.completeOralMeasurement()
                            renderRoute()
                        },
                    )
                    addView(
                        secondaryButton("Invalid Sample") {
                            routeState.markOralInvalidSample()
                            renderRoute()
                        },
                    )
                    addView(
                        secondaryButton("Cancel Session") {
                            routeState.cancelOralMeasurement()
                            renderRoute()
                        },
                    )
                }

                OralMeasurementFlowStep.COMPLETE -> {
                    val result = flowState.latestResult
                    addView(bodyCopy(result?.completionLabel ?: "Valid oral sample confirmed by the device."))
                    addView(headline(result?.scoreLabel ?: "Oral Health Score unavailable"))
                    addView(caption(result?.baselineProgressLabel ?: flowState.baselineProgress.progressLabel))
                    addView(bodyCopy(result?.baselineDetail ?: flowState.baselineProgress.detailLabel))
                    addView(
                        actionButton("Start Another Oral Session") {
                            routeState.startOralMeasurement()
                            renderRoute()
                        },
                    )
                }

                OralMeasurementFlowStep.FAILED -> {
                    addView(bodyCopy(flowState.recoveryMessage ?: "This oral session did not complete. Retry without saving a result."))
                    addView(
                        actionButton("Retry Oral Session") {
                            routeState.startOralMeasurement()
                            renderRoute()
                        },
                    )
                }

                OralMeasurementFlowStep.CANCELED -> {
                    addView(bodyCopy(flowState.recoveryMessage ?: "Session canceled. No oral score was saved."))
                    addView(
                        actionButton("Restart Oral Session") {
                            routeState.startOralMeasurement()
                            renderRoute()
                        },
                    )
                }
            }

            addView(
                actionButton("Return To ${context.feature.title}") {
                    routeState.returnToFeature()
                    renderRoute()
                },
            )
            addView(
                secondaryButton("Return To Home") {
                    routeState.returnHome()
                    renderRoute()
                },
            )
        }
    }

    private fun buildFatMeasurementActionView(context: SelectedFeatureContext): View {
        title = FeatureAction.MEASURE.title
        val flowState = routeState.fatMeasurementFlowState
        val nextSuggestedDelta = ((flowState.bestDeltaPercent ?: 0) + 4).coerceAtMost(24)

        return verticalLayout().apply {
            addView(headline("Fat-burning measurement"))
            addView(
                bodyCopy(
                    "Guide a repeated-reading fat session with baseline lock, current delta updates, best-delta tracking, and a final summary that keeps latest and best values separate.",
                ),
            )
            addView(bodyCopy("Selected feature: ${context.feature.title}"))
            addView(caption("Return route ID: ${context.lastVisitedRouteId}"))
            addView(headline("Target +${flowState.targetDeltaPercent}%"))
            addView(
                bodyCopy(
                    if (flowState.baselineLocked) {
                        "${flowState.readingCount} valid readings captured. Current delta ${flowState.currentDeltaPercent ?: 0}% • Best delta ${flowState.bestDeltaPercent ?: 0}%."
                    } else {
                        "Coaching starts with breath, hold, and blow guidance before the first valid baseline reading locks at 0%."
                    },
                ),
            )

            when (flowState.step) {
                FatMeasurementFlowStep.PREPARING -> {
                    addView(bodyCopy("Start the repeated-reading coaching flow and keep the device steady for baseline lock."))
                    addView(
                        actionButton("Start Fat-Burning Session") {
                            routeState.startFatMeasurement()
                            renderRoute()
                        },
                    )
                }

                FatMeasurementFlowStep.COACHING -> {
                    addView(bodyCopy("Coach the user through breath, hold, and blow. The first valid reading locks the session baseline at 0%."))
                    addView(
                        actionButton("Baseline Locked") {
                            routeState.lockFatBaseline()
                            renderRoute()
                        },
                    )
                    addView(
                        secondaryButton("Invalid Coaching Sample") {
                            routeState.failFatMeasurement("invalid_sample")
                            renderRoute()
                        },
                    )
                }

                FatMeasurementFlowStep.READING -> {
                    addView(bodyCopy("Track the current delta separately from the best delta. Later readings must not overwrite a stronger earlier delta."))
                    addView(caption("Current delta: +${flowState.currentDeltaPercent ?: 0}%"))
                    addView(caption("Best delta: +${flowState.bestDeltaPercent ?: 0}%"))
                    addView(
                        actionButton("Record Next Reading (+$nextSuggestedDelta%)") {
                            routeState.recordNextFatReading(nextSuggestedDelta)
                            renderRoute()
                        },
                    )
                    addView(
                        secondaryButton("Finish Session") {
                            routeState.requestFatFinish()
                            renderRoute()
                        },
                    )
                    addView(
                        secondaryButton("Invalid Sample") {
                            routeState.failFatMeasurement("invalid_sample")
                            renderRoute()
                        },
                    )
                    addView(
                        secondaryButton("Cancel Session") {
                            routeState.cancelFatMeasurement()
                            renderRoute()
                        },
                    )
                }

                FatMeasurementFlowStep.FINISH_PENDING -> {
                    val bestDelta = flowState.bestDeltaPercent ?: 0
                    val finalDelta = (bestDelta - 3).coerceAtLeast(4)
                    addView(bodyCopy("Finish is requested. Wait for the device-generated final summary before showing a completed result."))
                    addView(caption("Best delta so far: +$bestDelta%"))
                    addView(
                        actionButton("Receive Final Summary (+$finalDelta%)") {
                            routeState.completeFatMeasurement(finalDelta)
                            renderRoute()
                        },
                    )
                    addView(
                        secondaryButton("Disconnect Before Summary") {
                            routeState.failFatMeasurement("disconnect")
                            renderRoute()
                        },
                    )
                }

                FatMeasurementFlowStep.COMPLETE -> {
                    val result = flowState.latestResult
                    addView(headline(result?.finalDeltaLabel ?: "Final Fat Burn Delta unavailable"))
                    addView(bodyCopy(result?.bestDeltaLabel ?: "Best Fat Burn Delta unavailable"))
                    addView(caption(result?.progressLabel ?: "${flowState.readingCount} valid readings"))
                    addView(bodyCopy(result?.goalStatusLabel ?: "Session summary ready."))
                    addView(
                        actionButton("Start Another Fat Session") {
                            routeState.startFatMeasurement()
                            renderRoute()
                        },
                    )
                }

                FatMeasurementFlowStep.FAILED -> {
                    addView(bodyCopy(flowState.recoveryMessage ?: "This fat-burning session did not complete. Retry without saving a result."))
                    addView(
                        actionButton("Retry Fat Session") {
                            routeState.startFatMeasurement()
                            renderRoute()
                        },
                    )
                }

                FatMeasurementFlowStep.CANCELED -> {
                    addView(bodyCopy(flowState.recoveryMessage ?: "Session canceled. No fat-burning summary was saved."))
                    addView(
                        actionButton("Restart Fat Session") {
                            routeState.startFatMeasurement()
                            renderRoute()
                        },
                    )
                }
            }

            addView(
                actionButton("Return To ${context.feature.title}") {
                    routeState.returnToFeature()
                    renderRoute()
                },
            )
            addView(
                secondaryButton("Return To Home") {
                    routeState.returnHome()
                    renderRoute()
                },
            )
        }
    }

    private fun buildHistoryActionView(context: SelectedFeatureContext): View {
        title = FeatureAction.VIEW_HISTORY.title
        val entitlement = routeState.effectiveEntitlement
        val syncProjection = routeState.syncQueueProjectionFor(context.feature)
        val activeJob = routeState.activeSyncJobFor(context.feature)
        val exportPlatform = HealthExportPlatform.HEALTH_CONNECT
        val exportAuditSurface = routeState.exportAuditSurfaceFor(
            feature = context.feature,
            platform = exportPlatform,
        )

        return verticalLayout().apply {
            addView(headline("History and progress"))
            addView(
                bodyCopy(
                    "Render consumer-safe history from persisted completed summaries, keep pending versus synced state explicit, and show feature-specific progress context.",
                ),
            )
            addView(bodyCopy("Selected feature: ${context.feature.title}"))
            addView(caption("Return route ID: ${context.lastVisitedRouteId}"))
            entitlementBannerState(entitlement)?.let { banner ->
                addEntitlementBanner(banner)
            }

            when (context.feature) {
                FeatureKind.ORAL_HEALTH -> {
                    val oralSurface = routeState.sessionHistoryStoreState.oralHistorySurface()
                    addView(headline(oralSurface.baselineProgressLabel))
                    addView(bodyCopy(oralSurface.progressDetail))
                    oralSurface.latestStatusLabel?.let { addView(caption("Latest sync state: $it")) }
                    oralSurface.items.forEach { item ->
                        addView(bodyCopy(item.title))
                        addView(caption("${item.progressLabel} • ${item.syncLabel}"))
                        addView(bodyCopy(item.detail))
                    }
                }

                FeatureKind.FAT_BURNING -> {
                    val fatSurface = routeState.sessionHistoryStoreState.fatHistorySurface()
                    addView(headline(fatSurface.latestFinalDeltaLabel ?: "No fat-burning sessions yet"))
                    fatSurface.bestDeltaLabel?.let { addView(caption(it)) }
                    addView(caption("${fatSurface.pendingCount} pending • ${fatSurface.syncedCount} synced"))
                    fatSurface.items.forEach { item ->
                        addView(bodyCopy(item.title))
                        addView(caption("${item.finalDeltaLabel} • ${item.bestDeltaLabel} • ${item.syncLabel}"))
                        addView(bodyCopy(item.detail))
                    }
                }
            }

            addView(headline("Sync queue"))
            addView(
                caption(
                    "${syncProjection.pendingCount} pending • ${syncProjection.retryScheduledCount} retry • ${syncProjection.inFlightCount} in flight • ${syncProjection.poisonedCount} poisoned • ${syncProjection.syncedCount} synced",
                ),
            )
            syncProjection.nextEligibleJob?.let { nextJob ->
                addView(caption("Next eligible sync: ${nextJob.sessionId}"))
            }
            activeJob?.let { job ->
                addView(bodyCopy("Current sync attempt: ${job.sessionId}"))
                addView(caption("Idempotency key: ${job.idempotencyKey}"))
            }

            addView(
                actionButton("Record Completed Summary") {
                    routeState.recordDemoCompletedSession(context.feature)
                    renderRoute()
                },
            )
            addView(
                secondaryButton(
                    "Start Next Sync Attempt",
                    enabled = syncProjection.nextEligibleJob != null,
                ) {
                    routeState.beginNextSyncAttempt()
                    renderRoute()
                },
            )
            if (activeJob != null) {
                addView(
                    actionButton("Mark Sync Success") {
                        routeState.markSyncAttemptSucceeded(activeJob.sessionId)
                        renderRoute()
                    },
                )
                addView(
                    secondaryButton("Simulate Sync Failure") {
                        routeState.markSyncAttemptFailed(activeJob.sessionId, "offline_retry_required")
                        renderRoute()
                    },
                )
            }

            addView(headline("Health export"))
            addView(
                bodyCopy(
                    "Export the latest completed consumer-safe summary and keep an audit record for each success or failure attempt.",
                ),
            )
            addView(caption("Permission: ${exportAuditSurface.permissionState.wireValue}"))
            if (exportAuditSurface.permissionState == HealthExportPermissionState.DENIED) {
                addView(bodyCopy("${exportPlatform.title} permission is denied. Re-enable permission to retry export."))
            }
            exportAuditSurface.latestStatusLabel?.let { statusLabel ->
                addView(
                    caption(
                        "Latest export: $statusLabel via ${exportAuditSurface.latestPlatformTitle ?: "health platform"}",
                    ),
                )
            }
            exportAuditSurface.latestFailureReason?.let { failureReason ->
                addView(caption("Failure reason: $failureReason"))
            }
            addView(
                actionButton("Allow ${exportPlatform.title}") {
                    routeState.setExportPermission(exportPlatform, HealthExportPermissionState.GRANTED)
                    renderRoute()
                },
            )
            addView(
                secondaryButton("Deny ${exportPlatform.title}") {
                    routeState.setExportPermission(exportPlatform, HealthExportPermissionState.DENIED)
                    renderRoute()
                },
            )
            addView(
                actionButton(
                    "Export To ${exportPlatform.title}",
                    enabled = exportAuditSurface.permissionState == HealthExportPermissionState.GRANTED,
                ) {
                    routeState.exportLatestCompletedSummary(
                        feature = context.feature,
                        platform = exportPlatform,
                    )
                    renderRoute()
                },
            )
            addView(
                secondaryButton("Simulate Export Failure") {
                    routeState.failLatestCompletedSummaryExport(
                        feature = context.feature,
                        platform = exportPlatform,
                        reasonCode = "${exportPlatform.wireValue}_unavailable",
                    )
                    renderRoute()
                },
            )
            exportAuditSurface.records.take(3).forEach { record ->
                addView(bodyCopy("${record.platform.title}: ${record.statusLabel}"))
                addView(caption("Session ${record.sessionId} • token ${record.exportedResultToken}"))
            }

            addView(
                actionButton("Return To ${context.feature.title}") {
                    routeState.returnToFeature()
                    renderRoute()
                },
            )
            addView(
                secondaryButton("Return To Home") {
                    routeState.returnHome()
                    renderRoute()
                },
            )
        }
    }

    private fun buildGoalActionView(context: SelectedFeatureContext): View {
        title = FeatureAction.SET_GOALS.title
        val entitlement = routeState.effectiveEntitlement
        val goalSurface = goalEditorSurfaceState(entitlement)

        return verticalLayout().apply {
            addView(headline("Goal planner"))
            addView(
                bodyCopy(
                    "Create or revise a feature-specific goal and keep it cached locally so the same selection is available when this feature is reopened."
                )
            )
            addView(bodyCopy("Selected feature: ${context.feature.title}"))
            addView(caption("Return route ID: ${context.lastVisitedRouteId}"))
            entitlementBannerState(entitlement)?.let { banner ->
                addEntitlementBanner(banner)
            }
            goalSurface.detail?.let { detail ->
                addView(bodyCopy(detail))
            }

            val currentGoal = routeState.goalFor(context.feature)
            if (currentGoal != null) {
                addView(headline("Current cached goal"))
                addView(bodyCopy(currentGoal.summary))
                addView(caption("Target: ${currentGoal.targetLabel}"))
                addView(caption("Cadence: ${currentGoal.cadenceLabel}"))
                addView(caption("Local revision ${currentGoal.revision}"))
                addView(
                    secondaryButton("Clear Goal", enabled = goalSurface.canEditGoal) {
                        routeState.clearGoal(context.feature)
                        renderRoute()
                    }
                )
            } else {
                addView(
                    bodyCopy(
                        "No cached goal yet for ${context.feature.title}. " +
                            if (goalSurface.canEditGoal) {
                                "Choose a draft below to create one locally."
                            } else {
                                "Goal editing is paused until active entitlement returns."
                            }
                    )
                )
            }

            addView(headline("Goal drafts"))
            goalTemplatesFor(context.feature).forEach { template ->
                addView(
                    actionButton(template.title, enabled = goalSurface.canEditGoal) {
                        routeState.applyGoalTemplate(template)
                        renderRoute()
                    }
                )
                addView(bodyCopy(template.summary))
                addView(caption("Target: ${template.targetLabel} • ${template.cadenceLabel}"))
            }

            addView(
                actionButton("Return To ${context.feature.title}") {
                    routeState.returnToFeature()
                    renderRoute()
                }
            )
            addView(
                secondaryButton("Return To Home") {
                    routeState.returnHome()
                    renderRoute()
                }
            )
        }
    }

    private fun buildSuggestionActionView(context: SelectedFeatureContext): View {
        title = FeatureAction.GET_SUGGESTION.title
        val entitlement = routeState.effectiveEntitlement

        return verticalLayout().apply {
            addView(headline("Suggestion planner"))
            addView(
                bodyCopy(
                    "Refresh a feature-specific suggestion, cache it locally for reopen, and keep it tied to the current feature context."
                )
            )
            addView(bodyCopy("Selected feature: ${context.feature.title}"))
            addView(caption("Return route ID: ${context.lastVisitedRouteId}"))
            entitlementBannerState(entitlement)?.let { banner ->
                addEntitlementBanner(banner)
            }

            routeState.goalFor(context.feature)?.let { goal ->
                addView(headline("Goal context"))
                addView(bodyCopy(goal.summary))
                addView(caption("Target: ${goal.targetLabel} • ${goal.cadenceLabel}"))
            }

            val suggestion = routeState.suggestionFor(context.feature)
            val suggestionSurface = suggestionRefreshSurfaceState(
                entitlement = entitlement,
                hasCachedSuggestion = suggestion != null,
            )
            if (suggestion != null) {
                addView(headline("Cached suggestion"))
                addView(bodyCopy(suggestion.headline))
                addView(bodyCopy(suggestion.body))
                addView(caption("Support track: ${suggestion.supportingActionLabel}"))
                addView(caption("Source: ${suggestion.entryPoint.wireValue} • Refresh ${suggestion.refreshRevision}"))
            } else {
                addView(
                    bodyCopy(
                        "No cached suggestion yet for ${context.feature.title}. " +
                            if (suggestionSurface.canRefreshSuggestion) {
                                "Refresh one now and it will stay available when you re-enter this feature."
                            } else {
                                "A cached suggestion will appear here once active entitlement allows the first refresh."
                            }
                    )
                )
            }
            suggestionSurface.detail?.let { detail ->
                addView(bodyCopy(detail))
            }

            addView(
                actionButton(
                    "Refresh From Feature Hub",
                    enabled = suggestionSurface.canRefreshSuggestion,
                ) {
                    routeState.refreshSuggestion(
                        feature = context.feature,
                        entryPoint = SuggestionEntryPoint.FEATURE_HUB,
                    )
                    renderRoute()
                }
            )
            addView(
                secondaryButton(
                    "Refresh Result Follow-up",
                    enabled = suggestionSurface.canRefreshSuggestion,
                ) {
                    routeState.refreshSuggestion(
                        feature = context.feature,
                        entryPoint = SuggestionEntryPoint.RESULT_CONTEXT,
                    )
                    renderRoute()
                }
            )

            addView(
                actionButton("Return To ${context.feature.title}") {
                    routeState.returnToFeature()
                    renderRoute()
                }
            )
            addView(
                secondaryButton("Return To Home") {
                    routeState.returnHome()
                    renderRoute()
                }
            )
        }
    }

    private fun verticalLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
    }

    private fun LinearLayout.addEntitlementBanner(banner: EntitlementBannerState) {
        addView(headline(banner.title))
        addView(bodyCopy(banner.message))
    }

    private fun headline(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 26f
            setPadding(0, 0, 0, 24)
        }
    }

    private fun bodyCopy(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }
    }

    private fun caption(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }
    }

    private fun actionButton(
        text: String,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.58f
            setOnClickListener { onClick() }
        }
    }

    private fun secondaryButton(
        text: String,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            this.text = text
            isEnabled = enabled
            alpha = if (enabled) 0.88f else 0.52f
            setOnClickListener { onClick() }
        }
    }
}
