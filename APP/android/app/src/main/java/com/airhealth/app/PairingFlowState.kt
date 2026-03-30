package com.airhealth.app

enum class PairingStep(
    val routeId: String,
) {
    PERMISSION_PRIMER("permission_primer"),
    PERMISSION_DENIED("permission_denied"),
    DISCOVERING("discovering"),
    DEVICE_DISCOVERED("device_discovered"),
    CONNECTING("connecting"),
    INCOMPATIBLE("incompatible"),
    NOT_READY("not_ready"),
    CONNECTED("connected"),
    CLAIMING("claiming"),
    CLAIM_FAILED("claim_failed"),
    MODE_SELECTION("mode_selection"),
    SETUP_COMPLETE("setup_complete"),
    TIMEOUT("timeout"),
}

enum class SetupMode(
    val routeId: String,
    val title: String,
) {
    ORAL_HEALTH("oral_health", "Oral Health"),
    FAT_BURNING("fat_burning", "Fat Burning"),
}

data class DiscoveredDeviceSummary(
    val name: String,
    val protocolVersion: String,
    val signalLabel: String,
)

data class PairingFlowState(
    val step: PairingStep,
    val discoveredDevice: DiscoveredDeviceSummary? = null,
    val recoveryMessage: String? = null,
    val claimOwnerLabel: String? = null,
    val selectedMode: SetupMode? = null,
) {
    companion object {
        fun permissionPrimer(): PairingFlowState {
            return PairingFlowState(step = PairingStep.PERMISSION_PRIMER)
        }

        fun discovering(): PairingFlowState {
            return PairingFlowState(step = PairingStep.DISCOVERING)
        }

        fun defaultDevice(): DiscoveredDeviceSummary {
            return DiscoveredDeviceSummary(
                name = "AirHealth Breath Sensor",
                protocolVersion = "consumer-v2",
                signalLabel = "Strong",
            )
        }
    }
}
