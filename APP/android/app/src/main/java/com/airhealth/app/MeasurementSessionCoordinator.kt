package com.airhealth.app

enum class MeasurementSessionPhase(
    val routeId: String,
) {
    READY("ready"),
    WARMING("warming"),
    MEASURING("measuring"),
    PAUSED("paused"),
    FAILED("failed"),
    CANCELED("canceled"),
    COMPLETE("complete"),

    ;

    val isTerminal: Boolean
        get() = this == FAILED || this == CANCELED || this == COMPLETE
}

data class MeasurementRecoveryMarker(
    val sessionId: String,
    val lastStablePhase: MeasurementSessionPhase,
    val replayRequired: Boolean,
)

data class MeasurementTerminalSummary(
    val resultToken: String,
)

data class MeasurementSessionState(
    val sessionId: String,
    val feature: FeatureKind,
    val phase: MeasurementSessionPhase,
    val recoveryMarker: MeasurementRecoveryMarker,
    val terminalSummary: MeasurementTerminalSummary? = null,
    val failureReason: String? = null,
    val cancellationReason: String? = null,
    val lastEventCode: String? = null,
) {
    companion object {
        fun begin(
            sessionId: String,
            feature: FeatureKind,
        ): MeasurementSessionState {
            return MeasurementSessionState(
                sessionId = sessionId,
                feature = feature,
                phase = MeasurementSessionPhase.READY,
                recoveryMarker = MeasurementRecoveryMarker(
                    sessionId = sessionId,
                    lastStablePhase = MeasurementSessionPhase.READY,
                    replayRequired = false,
                ),
                lastEventCode = "session_prepared",
            )
        }
    }
}

sealed interface MeasurementBleEvent {
    val code: String

    data object WarmupStarted : MeasurementBleEvent {
        override val code: String = "warmup_started"
    }

    data object MeasurementStarted : MeasurementBleEvent {
        override val code: String = "measurement_started"
    }

    data object MeasurementPaused : MeasurementBleEvent {
        override val code: String = "measurement_paused"
    }

    data object MeasurementResumed : MeasurementBleEvent {
        override val code: String = "measurement_resumed"
    }

    data class TerminalReadingAvailable(
        val summary: MeasurementTerminalSummary,
    ) : MeasurementBleEvent {
        override val code: String = "terminal_reading_available"
    }

    data object TerminalReadingConfirmed : MeasurementBleEvent {
        override val code: String = "terminal_reading_confirmed"
    }

    data class DeviceDisconnected(
        val replayRequired: Boolean,
    ) : MeasurementBleEvent {
        override val code: String = "device_disconnected"
    }

    data class MeasurementFailed(
        val reasonCode: String,
    ) : MeasurementBleEvent {
        override val code: String = "measurement_failed"
    }

    data class SessionCanceled(
        val reasonCode: String,
    ) : MeasurementBleEvent {
        override val code: String = "session_canceled"
    }
}

object MeasurementSessionCoordinator {
    fun reduce(
        state: MeasurementSessionState,
        event: MeasurementBleEvent,
    ): MeasurementSessionState {
        if (state.phase.isTerminal) {
            return state
        }

        return when (event) {
            MeasurementBleEvent.WarmupStarted -> state.copy(
                phase = MeasurementSessionPhase.WARMING,
                recoveryMarker = state.recoveryMarker.copy(
                    lastStablePhase = MeasurementSessionPhase.WARMING,
                    replayRequired = false,
                ),
                lastEventCode = event.code,
            )

            MeasurementBleEvent.MeasurementStarted -> state.copy(
                phase = MeasurementSessionPhase.MEASURING,
                recoveryMarker = state.recoveryMarker.copy(
                    lastStablePhase = MeasurementSessionPhase.MEASURING,
                    replayRequired = false,
                ),
                lastEventCode = event.code,
            )

            MeasurementBleEvent.MeasurementPaused -> state.copy(
                phase = MeasurementSessionPhase.PAUSED,
                recoveryMarker = state.recoveryMarker.copy(
                    lastStablePhase = MeasurementSessionPhase.PAUSED,
                    replayRequired = false,
                ),
                lastEventCode = event.code,
            )

            MeasurementBleEvent.MeasurementResumed -> state.copy(
                phase = MeasurementSessionPhase.MEASURING,
                recoveryMarker = state.recoveryMarker.copy(
                    lastStablePhase = MeasurementSessionPhase.MEASURING,
                    replayRequired = false,
                ),
                lastEventCode = event.code,
            )

            is MeasurementBleEvent.TerminalReadingAvailable -> state.copy(
                terminalSummary = event.summary,
                lastEventCode = event.code,
            )

            MeasurementBleEvent.TerminalReadingConfirmed -> state.copy(
                phase = MeasurementSessionPhase.COMPLETE,
                recoveryMarker = state.recoveryMarker.copy(
                    lastStablePhase = MeasurementSessionPhase.COMPLETE,
                    replayRequired = false,
                ),
                lastEventCode = event.code,
            )

            is MeasurementBleEvent.DeviceDisconnected -> state.copy(
                phase = MeasurementSessionPhase.PAUSED,
                recoveryMarker = state.recoveryMarker.copy(
                    replayRequired = event.replayRequired,
                ),
                lastEventCode = event.code,
            )

            is MeasurementBleEvent.MeasurementFailed -> state.copy(
                phase = MeasurementSessionPhase.FAILED,
                failureReason = event.reasonCode,
                recoveryMarker = state.recoveryMarker.copy(
                    replayRequired = true,
                ),
                lastEventCode = event.code,
            )

            is MeasurementBleEvent.SessionCanceled -> state.copy(
                phase = MeasurementSessionPhase.CANCELED,
                cancellationReason = event.reasonCode,
                recoveryMarker = state.recoveryMarker.copy(
                    replayRequired = false,
                ),
                lastEventCode = event.code,
            )
        }
    }
}
