package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementSessionCoordinatorTest {
    @Test
    fun reducerMapsNonTerminalEventsDeterministically() {
        var state = MeasurementSessionState.begin(
            sessionId = "session-oral-001",
            feature = FeatureKind.ORAL_HEALTH,
        )

        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.WarmupStarted)
        assertEquals(MeasurementSessionPhase.WARMING, state.phase)
        assertEquals("warmup_started", state.lastEventCode)

        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.MeasurementStarted)
        assertEquals(MeasurementSessionPhase.MEASURING, state.phase)
        assertEquals("measurement_started", state.lastEventCode)

        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.MeasurementPaused)
        assertEquals(MeasurementSessionPhase.PAUSED, state.phase)
        assertEquals(MeasurementSessionPhase.PAUSED, state.recoveryMarker.lastStablePhase)

        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.MeasurementResumed)
        assertEquals(MeasurementSessionPhase.MEASURING, state.phase)
        assertEquals(MeasurementSessionPhase.MEASURING, state.recoveryMarker.lastStablePhase)
    }

    @Test
    fun completionRequiresTerminalConfirmation() {
        var state = MeasurementSessionState.begin(
            sessionId = "session-fat-001",
            feature = FeatureKind.FAT_BURNING,
        )

        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.WarmupStarted)
        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.MeasurementStarted)
        state = MeasurementSessionCoordinator.reduce(
            state,
            MeasurementBleEvent.TerminalReadingAvailable(
                MeasurementTerminalSummary(resultToken = "fat-summary-token"),
            ),
        )

        assertEquals(MeasurementSessionPhase.MEASURING, state.phase)
        assertNotNull(state.terminalSummary)

        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.TerminalReadingConfirmed)

        assertEquals(MeasurementSessionPhase.COMPLETE, state.phase)
        assertEquals(MeasurementSessionPhase.COMPLETE, state.recoveryMarker.lastStablePhase)
        assertFalse(state.recoveryMarker.replayRequired)
    }

    @Test
    fun failureAndCancellationPreserveFollowUpState() {
        var state = MeasurementSessionState.begin(
            sessionId = "session-oral-002",
            feature = FeatureKind.ORAL_HEALTH,
        )

        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.WarmupStarted)
        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.MeasurementStarted)
        state = MeasurementSessionCoordinator.reduce(
            state,
            MeasurementBleEvent.DeviceDisconnected(replayRequired = true),
        )

        assertEquals(MeasurementSessionPhase.PAUSED, state.phase)
        assertTrue(state.recoveryMarker.replayRequired)

        val failedState = MeasurementSessionCoordinator.reduce(
            state,
            MeasurementBleEvent.MeasurementFailed(reasonCode = "sensor_timeout"),
        )
        assertEquals(MeasurementSessionPhase.FAILED, failedState.phase)
        assertEquals("sensor_timeout", failedState.failureReason)
        assertTrue(failedState.recoveryMarker.replayRequired)

        val canceledState = MeasurementSessionCoordinator.reduce(
            state,
            MeasurementBleEvent.SessionCanceled(reasonCode = "user_backed_out"),
        )
        assertEquals(MeasurementSessionPhase.CANCELED, canceledState.phase)
        assertEquals("user_backed_out", canceledState.cancellationReason)
        assertFalse(canceledState.recoveryMarker.replayRequired)
    }

    @Test
    fun terminalPhasesIgnoreLateEvents() {
        val completedState = MeasurementSessionCoordinator.reduce(
            MeasurementSessionCoordinator.reduce(
                MeasurementSessionCoordinator.reduce(
                    MeasurementSessionState.begin(
                        sessionId = "session-fat-002",
                        feature = FeatureKind.FAT_BURNING,
                    ),
                    MeasurementBleEvent.MeasurementStarted,
                ),
                MeasurementBleEvent.TerminalReadingAvailable(
                    MeasurementTerminalSummary(resultToken = "terminal-token"),
                ),
            ),
            MeasurementBleEvent.TerminalReadingConfirmed,
        )

        val completedAfterLateFailure = MeasurementSessionCoordinator.reduce(
            completedState,
            MeasurementBleEvent.MeasurementFailed(reasonCode = "late_failure"),
        )
        assertEquals(MeasurementSessionPhase.COMPLETE, completedAfterLateFailure.phase)
        assertEquals("terminal_reading_confirmed", completedAfterLateFailure.lastEventCode)
        assertEquals("terminal-token", completedAfterLateFailure.terminalSummary?.resultToken)

        val failedState = MeasurementSessionCoordinator.reduce(
            MeasurementSessionState.begin(
                sessionId = "session-oral-003",
                feature = FeatureKind.ORAL_HEALTH,
            ),
            MeasurementBleEvent.MeasurementFailed(reasonCode = "sensor_timeout"),
        )
        val failedAfterLateCancel = MeasurementSessionCoordinator.reduce(
            failedState,
            MeasurementBleEvent.SessionCanceled(reasonCode = "user_backed_out"),
        )
        assertEquals(MeasurementSessionPhase.FAILED, failedAfterLateCancel.phase)
        assertEquals("sensor_timeout", failedAfterLateCancel.failureReason)
        assertEquals("measurement_failed", failedAfterLateCancel.lastEventCode)
    }

    @Test
    fun outOfOrderConfirmationWaitsForTerminalPayload() {
        var state = MeasurementSessionState.begin(
            sessionId = "session-fat-003",
            feature = FeatureKind.FAT_BURNING,
        )

        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.MeasurementStarted)
        state = MeasurementSessionCoordinator.reduce(state, MeasurementBleEvent.TerminalReadingConfirmed)

        assertEquals(MeasurementSessionPhase.MEASURING, state.phase)
        assertTrue(state.terminalConfirmationReceived)
        assertEquals("terminal_reading_confirmed", state.lastEventCode)

        state = MeasurementSessionCoordinator.reduce(
            state,
            MeasurementBleEvent.TerminalReadingAvailable(
                MeasurementTerminalSummary(resultToken = "out-of-order-token"),
            ),
        )

        assertEquals(MeasurementSessionPhase.COMPLETE, state.phase)
        assertEquals(MeasurementSessionPhase.COMPLETE, state.recoveryMarker.lastStablePhase)
        assertFalse(state.recoveryMarker.replayRequired)
        assertEquals("out-of-order-token", state.terminalSummary?.resultToken)
    }
}
