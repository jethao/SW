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

        return verticalLayout().apply {
            addView(
                headline("Choose a feature")
            )
            addView(
                bodyCopy(
                    "Start from the home feature hub, keep the selected feature context, and route to the next action from there."
                )
            )
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

            FeatureAction.entries.forEach { action ->
                addView(
                    actionButton(action.title) {
                        routeState.openAction(action)
                        renderRoute()
                    }
                )
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
        title = action.title

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
                    addView(
                        secondaryButton("Try ${candidate.title}") {
                            routeState.openAction(candidate)
                            renderRoute()
                        }
                    )
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

    private fun verticalLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
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
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
    }

    private fun secondaryButton(
        text: String,
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            this.text = text
            alpha = 0.88f
            setOnClickListener { onClick() }
        }
    }
}
