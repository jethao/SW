package com.airhealth.app

enum class PairingRecoveryAction(
    val code: String,
) {
    SURFACED("surfaced"),
    RETRY("retry"),
    EXIT("exit"),
}

data class PairingRecoveryEvent(
    val failureStep: String,
    val recoveryAction: String,
) {
    fun payload(): Map<String, String> {
        return linkedMapOf(
            "failure_step" to failureStep,
            "recovery_action" to recoveryAction,
        )
    }
}

class PairingRecoveryAnalytics {
    private val recordedEvents = mutableListOf<PairingRecoveryEvent>()

    val events: List<PairingRecoveryEvent>
        get() = recordedEvents

    fun record(
        failureStep: PairingStep,
        recoveryAction: PairingRecoveryAction,
    ) {
        recordedEvents += PairingRecoveryEvent(
            failureStep = failureStep.routeId,
            recoveryAction = recoveryAction.code,
        )
    }
}
