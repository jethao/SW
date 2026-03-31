package com.airhealth.app

enum class MeasurementRecoveryStage {
    INTERRUPTED,
    RECONNECTING,
    RECOVERED,
    FAILED,
}

data class MeasurementRecoveryState(
    val feature: FeatureKind,
    val sessionId: String,
    val stage: MeasurementRecoveryStage,
    val replayRequired: Boolean,
    val statusMessage: String,
    val recoveredResultToken: String? = null,
) {
    companion object {
        fun interrupted(
            feature: FeatureKind,
            sessionId: String,
        ): MeasurementRecoveryState {
            return MeasurementRecoveryState(
                feature = feature,
                sessionId = sessionId,
                stage = MeasurementRecoveryStage.INTERRUPTED,
                replayRequired = true,
                statusMessage = "Connection lost. Reconnect to query the device for the terminal result before deciding whether this session failed.",
            )
        }
    }
}
