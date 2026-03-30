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

    private fun buildHistoryActionView(context: SelectedFeatureContext): View {
        title = FeatureAction.VIEW_HISTORY.title
        val entitlement = routeState.effectiveEntitlement
        val syncProjection = routeState.syncQueueProjectionFor(context.feature)
        val activeJob = routeState.activeSyncJobFor(context.feature)

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
