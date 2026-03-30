package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OralMeasurementFlowStateTest {
    @Test
    fun validCompletionPromotesBaselineProgressAndCapturesResult() {
        var state = OralMeasurementFlowState()

        state = OralMeasurementFlowCoordinator.start(
            state = state,
            sessionId = "oral-session-001",
        )
        state = OralMeasurementFlowCoordinator.markWarmupPassed(state)
        state = OralMeasurementFlowCoordinator.complete(
            state = state,
            oralHealthScore = 61,
        )

        assertEquals(OralMeasurementFlowStep.COMPLETE, state.step)
        assertEquals(1, state.baselineProgress.completedValidSessions)
        assertEquals("1/5 baseline sessions", state.latestResult?.baselineProgressLabel)
        assertEquals("Oral Health Score 61", state.latestResult?.scoreLabel)
        assertEquals(MeasurementSessionPhase.COMPLETE, state.activeSession?.phase)
        assertEquals(
            "oral-session-001-score-61",
            state.activeSession?.terminalSummary?.resultToken,
        )
    }

    @Test
    fun invalidSampleDoesNotSurfaceCompletedResult() {
        var state = OralMeasurementFlowState()

        state = OralMeasurementFlowCoordinator.start(
            state = state,
            sessionId = "oral-session-002",
        )
        state = OralMeasurementFlowCoordinator.markWarmupPassed(state)
        state = OralMeasurementFlowCoordinator.markInvalidSample(state)

        assertEquals(OralMeasurementFlowStep.FAILED, state.step)
        assertNull(state.latestResult)
        assertEquals(0, state.baselineProgress.completedValidSessions)
        assertEquals("invalid_sample", state.activeSession?.failureReason)
    }

    @Test
    fun cancelLeavesRecoveryMessageAndNoSavedResult() {
        var state = OralMeasurementFlowState()

        state = OralMeasurementFlowCoordinator.start(
            state = state,
            sessionId = "oral-session-003",
        )
        state = OralMeasurementFlowCoordinator.markWarmupPassed(state)
        state = OralMeasurementFlowCoordinator.cancel(state)

        assertEquals(OralMeasurementFlowStep.CANCELED, state.step)
        assertNull(state.latestResult)
        assertTrue(state.recoveryMessage!!.contains("No completed oral result"))
        assertEquals("user_canceled", state.activeSession?.cancellationReason)
    }
}
