package com.airhealth.app

enum class PairingStep(
    val routeId: String,
) {
    PERMISSION_PRIMER("permission_primer"),
    PERMISSION_DENIED("permission_denied"),
    DISCOVERING("discovering"),
    DEVICE_DISCOVERED("device_discovered"),
    CONNECTING("connecting"),
    CONNECTED("connected"),
    TIMEOUT("timeout"),
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
