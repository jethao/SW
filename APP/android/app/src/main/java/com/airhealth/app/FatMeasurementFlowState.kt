package com.airhealth.app

data class FatMeasurementResultSurface(
    val finalDeltaPercent: Int,
    val bestDeltaPercent: Int,
    val readingCount: Int,
    val targetDeltaPercent: Int,
    val finalDeltaLabel: String,
    val bestDeltaLabel: String,
    val progressLabel: String,
    val goalStatusLabel: String,
)

enum class FatMeasurementFlowStep {
    PREPARING,
    COACHING,
    READING,
    FINISH_PENDING,
    COMPLETE,
    FAILED,
    CANCELED,
}

data class FatMeasurementFlowState(
    val activeSession: MeasurementSessionState? = null,
    val readingCount: Int = 0,
    val targetDeltaPercent: Int = 18,
    val currentDeltaPercent: Int? = null,
    val bestDeltaPercent: Int? = null,
    val latestResult: FatMeasurementResultSurface? = null,
    val recoveryMessage: String? = null,
    val finishRequested: Boolean = false,
) {
    val step: FatMeasurementFlowStep
        get() = when {
            activeSession == null -> FatMeasurementFlowStep.PREPARING
            activeSession.phase == MeasurementSessionPhase.FAILED -> FatMeasurementFlowStep.FAILED
            activeSession.phase == MeasurementSessionPhase.CANCELED -> FatMeasurementFlowStep.CANCELED
            activeSession.phase == MeasurementSessionPhase.COMPLETE -> FatMeasurementFlowStep.COMPLETE
            finishRequested -> FatMeasurementFlowStep.FINISH_PENDING
            readingCount == 0 -> FatMeasurementFlowStep.COACHING
            else -> FatMeasurementFlowStep.READING
        }

    val baselineLocked: Boolean
        get() = readingCount > 0
}

object FatMeasurementFlowCoordinator {
    fun start(
        state: FatMeasurementFlowState,
        sessionId: String,
    ): FatMeasurementFlowState {
        val session = MeasurementSessionCoordinator.reduce(
            state = MeasurementSessionState.begin(
                sessionId = sessionId,
                feature = FeatureKind.FAT_BURNING,
            ),
            event = MeasurementBleEvent.WarmupStarted,
        )
        return state.copy(
            activeSession = session,
            readingCount = 0,
            currentDeltaPercent = null,
            bestDeltaPercent = null,
            latestResult = null,
            recoveryMessage = null,
            finishRequested = false,
        )
    }

    fun completeCoachingWithFirstReading(
        state: FatMeasurementFlowState,
        deltaPercent: Int,
    ): FatMeasurementFlowState {
        val session = state.activeSession ?: return state
        val measuringSession = MeasurementSessionCoordinator.reduce(
            state = session,
            event = MeasurementBleEvent.MeasurementStarted,
        )
        return state.copy(
            activeSession = measuringSession,
            readingCount = 1,
            currentDeltaPercent = deltaPercent,
            bestDeltaPercent = deltaPercent,
            latestResult = null,
            recoveryMessage = null,
            finishRequested = false,
        )
    }

    fun recordReading(
        state: FatMeasurementFlowState,
        deltaPercent: Int,
    ): FatMeasurementFlowState {
        val session = state.activeSession ?: return state
        return state.copy(
            activeSession = session,
            readingCount = state.readingCount + 1,
            currentDeltaPercent = deltaPercent,
            bestDeltaPercent = maxOf(state.bestDeltaPercent ?: deltaPercent, deltaPercent),
            recoveryMessage = null,
        )
    }

    fun requestFinish(state: FatMeasurementFlowState): FatMeasurementFlowState {
        val session = state.activeSession ?: return state
        if (state.readingCount == 0) return state
        return state.copy(
            activeSession = session,
            finishRequested = true,
            recoveryMessage = null,
        )
    }

    fun complete(
        state: FatMeasurementFlowState,
        finalDeltaPercent: Int,
    ): FatMeasurementFlowState {
        val session = state.activeSession ?: return state
        val bestDelta = maxOf(state.bestDeltaPercent ?: finalDeltaPercent, finalDeltaPercent)
        val resultToken = "${session.sessionId}-fat-$bestDelta-$finalDeltaPercent"
        val completedSession = MeasurementSessionCoordinator.reduce(
            state = MeasurementSessionCoordinator.reduce(
                state = session,
                event = MeasurementBleEvent.TerminalReadingAvailable(
                    MeasurementTerminalSummary(resultToken = resultToken),
                ),
            ),
            event = MeasurementBleEvent.TerminalReadingConfirmed,
        )
        val resultSurface = FatMeasurementResultSurface(
            finalDeltaPercent = finalDeltaPercent,
            bestDeltaPercent = bestDelta,
            readingCount = state.readingCount,
            targetDeltaPercent = state.targetDeltaPercent,
            finalDeltaLabel = "Final Fat Burn Delta +$finalDeltaPercent%",
            bestDeltaLabel = "Best Fat Burn Delta +$bestDelta%",
            progressLabel = "${state.readingCount} valid readings • target +${state.targetDeltaPercent}%",
            goalStatusLabel = if (bestDelta >= state.targetDeltaPercent) {
                "Goal reached during this session."
            } else {
                "Keep reading until your best delta reaches +${state.targetDeltaPercent}%."
            },
        )
        return state.copy(
            activeSession = completedSession,
            currentDeltaPercent = finalDeltaPercent,
            bestDeltaPercent = bestDelta,
            latestResult = resultSurface,
            recoveryMessage = null,
            finishRequested = false,
        )
    }

    fun fail(
        state: FatMeasurementFlowState,
        reasonCode: String,
        recoveryMessage: String,
    ): FatMeasurementFlowState {
        val session = state.activeSession ?: return state
        return state.copy(
            activeSession = MeasurementSessionCoordinator.reduce(
                state = session,
                event = MeasurementBleEvent.MeasurementFailed(reasonCode = reasonCode),
            ),
            latestResult = null,
            recoveryMessage = recoveryMessage,
            finishRequested = false,
        )
    }

    fun cancel(state: FatMeasurementFlowState): FatMeasurementFlowState {
        val session = state.activeSession ?: return state
        return state.copy(
            activeSession = MeasurementSessionCoordinator.reduce(
                state = session,
                event = MeasurementBleEvent.SessionCanceled(reasonCode = "user_canceled"),
            ),
            latestResult = null,
            recoveryMessage = "Session canceled. No fat-burning summary was saved.",
            finishRequested = false,
        )
    }
}
