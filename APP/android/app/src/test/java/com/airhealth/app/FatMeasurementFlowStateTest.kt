package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FatMeasurementFlowStateTest {
    @Test
    fun repeatedReadingsPreserveBestDeltaIntoCompletion() {
        var state = FatMeasurementFlowState()

        state = FatMeasurementFlowCoordinator.start(state, "fat-session-001")
        state = FatMeasurementFlowCoordinator.completeCoachingWithFirstReading(state, 0)
        state = FatMeasurementFlowCoordinator.recordReading(state, 12)
        state = FatMeasurementFlowCoordinator.recordReading(state, 9)
        state = FatMeasurementFlowCoordinator.requestFinish(state)
        state = FatMeasurementFlowCoordinator.complete(state, 7)

        assertEquals(FatMeasurementFlowStep.COMPLETE, state.step)
        assertEquals(3, state.readingCount)
        assertEquals(12, state.bestDeltaPercent)
        assertEquals("Best Fat Burn Delta +12%", state.latestResult?.bestDeltaLabel)
        assertEquals("Final Fat Burn Delta +7%", state.latestResult?.finalDeltaLabel)
    }

    @Test
    fun failedSessionDoesNotEmitCompletedResult() {
        var state = FatMeasurementFlowState()

        state = FatMeasurementFlowCoordinator.start(state, "fat-session-002")
        state = FatMeasurementFlowCoordinator.completeCoachingWithFirstReading(state, 0)
        state = FatMeasurementFlowCoordinator.fail(
            state = state,
            reasonCode = "invalid_sample",
            recoveryMessage = "The reading was not valid. Retry without saving a result.",
        )

        assertEquals(FatMeasurementFlowStep.FAILED, state.step)
        assertNull(state.latestResult)
        assertEquals("invalid_sample", state.activeSession?.failureReason)
    }

    @Test
    fun cancelLeavesNoSummaryAndRecoveryCopy() {
        var state = FatMeasurementFlowState()

        state = FatMeasurementFlowCoordinator.start(state, "fat-session-003")
        state = FatMeasurementFlowCoordinator.completeCoachingWithFirstReading(state, 0)
        state = FatMeasurementFlowCoordinator.cancel(state)

        assertEquals(FatMeasurementFlowStep.CANCELED, state.step)
        assertNull(state.latestResult)
        assertTrue(state.recoveryMessage!!.contains("No fat-burning summary"))
    }
}
