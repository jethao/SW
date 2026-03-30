package com.airhealth.app

private const val ORAL_BASELINE_REQUIRED_SESSIONS = 5

data class OralBaselineProgress(
    val completedValidSessions: Int = 0,
    val requiredValidSessions: Int = ORAL_BASELINE_REQUIRED_SESSIONS,
) {
    val clampedCompletedSessions: Int
        get() = completedValidSessions.coerceIn(0, requiredValidSessions)

    val progressLabel: String
        get() = "${clampedCompletedSessions}/${requiredValidSessions} baseline sessions"

    val detailLabel: String
        get() = if (clampedCompletedSessions >= requiredValidSessions) {
            "Baseline locked. Future oral scores compare against your established average."
        } else {
            "Complete ${requiredValidSessions - clampedCompletedSessions} more valid oral sessions to lock your baseline."
        }
}

data class OralMeasurementResultSurface(
    val oralHealthScore: Int,
    val scoreLabel: String,
    val baselineProgressLabel: String,
    val baselineDetail: String,
    val completionLabel: String,
)

enum class OralMeasurementFlowStep {
    PREPARING,
    WARMING,
    MEASURING,
    COMPLETE,
    FAILED,
    CANCELED,
}

data class OralMeasurementFlowState(
    val activeSession: MeasurementSessionState? = null,
    val baselineProgress: OralBaselineProgress = OralBaselineProgress(),
    val latestResult: OralMeasurementResultSurface? = null,
    val recoveryMessage: String? = null,
) {
    val step: OralMeasurementFlowStep
        get() = when (activeSession?.phase) {
            null -> OralMeasurementFlowStep.PREPARING
            MeasurementSessionPhase.READY,
            MeasurementSessionPhase.WARMING,
            -> OralMeasurementFlowStep.WARMING
            MeasurementSessionPhase.MEASURING,
            MeasurementSessionPhase.PAUSED,
            -> OralMeasurementFlowStep.MEASURING
            MeasurementSessionPhase.COMPLETE -> OralMeasurementFlowStep.COMPLETE
            MeasurementSessionPhase.FAILED -> OralMeasurementFlowStep.FAILED
            MeasurementSessionPhase.CANCELED -> OralMeasurementFlowStep.CANCELED
        }
}

object OralMeasurementFlowCoordinator {
    fun start(
        state: OralMeasurementFlowState,
        sessionId: String,
    ): OralMeasurementFlowState {
        val session = MeasurementSessionCoordinator.reduce(
            state = MeasurementSessionState.begin(
                sessionId = sessionId,
                feature = FeatureKind.ORAL_HEALTH,
            ),
            event = MeasurementBleEvent.WarmupStarted,
        )
        return state.copy(
            activeSession = session,
            latestResult = null,
            recoveryMessage = null,
        )
    }

    fun markWarmupPassed(state: OralMeasurementFlowState): OralMeasurementFlowState {
        val session = state.activeSession ?: return state
        return state.copy(
            activeSession = MeasurementSessionCoordinator.reduce(
                state = session,
                event = MeasurementBleEvent.MeasurementStarted,
            ),
            recoveryMessage = null,
        )
    }

    fun markWarmupFailed(state: OralMeasurementFlowState): OralMeasurementFlowState {
        return fail(
            state = state,
            reasonCode = "warmup_failed",
            recoveryMessage = "Warm-up did not complete cleanly. Retry before recording an oral score.",
        )
    }

    fun markInvalidSample(state: OralMeasurementFlowState): OralMeasurementFlowState {
        return fail(
            state = state,
            reasonCode = "invalid_sample",
            recoveryMessage = "The sample was not valid. Retry without saving a completed result.",
        )
    }

    fun cancel(state: OralMeasurementFlowState): OralMeasurementFlowState {
        val session = state.activeSession ?: return state
        return state.copy(
            activeSession = MeasurementSessionCoordinator.reduce(
                state = session,
                event = MeasurementBleEvent.SessionCanceled(reasonCode = "user_canceled"),
            ),
            latestResult = null,
            recoveryMessage = "Session canceled. No completed oral result was saved.",
        )
    }

    fun complete(
        state: OralMeasurementFlowState,
        oralHealthScore: Int,
    ): OralMeasurementFlowState {
        val session = state.activeSession ?: return state
        val resultToken = "${session.sessionId}-score-$oralHealthScore"
        val completedSession = MeasurementSessionCoordinator.reduce(
            state = MeasurementSessionCoordinator.reduce(
                state = session,
                event = MeasurementBleEvent.TerminalReadingAvailable(
                    MeasurementTerminalSummary(resultToken = resultToken),
                ),
            ),
            event = MeasurementBleEvent.TerminalReadingConfirmed,
        )
        val nextBaselineProgress = state.baselineProgress.copy(
            completedValidSessions = (state.baselineProgress.completedValidSessions + 1)
                .coerceAtMost(state.baselineProgress.requiredValidSessions),
        )
        val resultSurface = OralMeasurementResultSurface(
            oralHealthScore = oralHealthScore,
            scoreLabel = "Oral Health Score $oralHealthScore",
            baselineProgressLabel = nextBaselineProgress.progressLabel,
            baselineDetail = nextBaselineProgress.detailLabel,
            completionLabel = "Valid oral sample confirmed by the device.",
        )
        return state.copy(
            activeSession = completedSession,
            baselineProgress = nextBaselineProgress,
            latestResult = resultSurface,
            recoveryMessage = null,
        )
    }

    private fun fail(
        state: OralMeasurementFlowState,
        reasonCode: String,
        recoveryMessage: String,
    ): OralMeasurementFlowState {
        val session = state.activeSession ?: return state
        return state.copy(
            activeSession = MeasurementSessionCoordinator.reduce(
                state = session,
                event = MeasurementBleEvent.MeasurementFailed(reasonCode = reasonCode),
            ),
            latestResult = null,
            recoveryMessage = recoveryMessage,
        )
    }
}
