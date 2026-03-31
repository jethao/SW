package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementTerminalCoverageIntegrationTest {
    private fun activeRouteState(): FeatureHubRouteState {
        return FeatureHubRouteState(currentTimeMillis = { 1_000L }).apply {
            replaceEntitlementCacheState(
                EntitlementCacheState(
                    snapshot = CachedEntitlementSnapshot(
                        sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                        verifiedAtEpochMillis = 1_000L,
                    ),
                    isBackendReachable = true,
                    lastVerificationAttemptAtEpochMillis = 1_000L,
                ),
            )
        }
    }

    @Test
    fun oralHappyPathReachesCompletedResultSurface() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.startOralMeasurement()
        routeState.markOralWarmupPassed()
        routeState.completeOralMeasurement()

        assertEquals(OralMeasurementFlowStep.COMPLETE, routeState.oralMeasurementFlowState.step)
        assertEquals(
            MeasurementSessionPhase.COMPLETE,
            routeState.oralMeasurementFlowState.activeSession?.phase,
        )
        assertTrue(
            routeState.oralMeasurementFlowState.latestResult?.completionLabel
                ?.contains("confirmed by the device") == true,
        )
    }

    @Test
    fun oralCancelAndFailurePathsDoNotProduceCompletedSummary() {
        val canceledRouteState = activeRouteState()
        canceledRouteState.openFeature(FeatureKind.ORAL_HEALTH)
        canceledRouteState.openAction(FeatureAction.MEASURE)
        canceledRouteState.startOralMeasurement()
        canceledRouteState.cancelOralMeasurement()

        assertEquals(OralMeasurementFlowStep.CANCELED, canceledRouteState.oralMeasurementFlowState.step)
        assertNull(canceledRouteState.oralMeasurementFlowState.latestResult)

        val failedRouteState = activeRouteState()
        failedRouteState.openFeature(FeatureKind.ORAL_HEALTH)
        failedRouteState.openAction(FeatureAction.MEASURE)
        failedRouteState.startOralMeasurement()
        failedRouteState.markOralWarmupPassed()
        failedRouteState.markOralInvalidSample()

        assertEquals(OralMeasurementFlowStep.FAILED, failedRouteState.oralMeasurementFlowState.step)
        assertNull(failedRouteState.oralMeasurementFlowState.latestResult)
        assertEquals(
            "invalid_sample",
            failedRouteState.oralMeasurementFlowState.activeSession?.failureReason,
        )
    }

    @Test
    fun fatHappyPathReachesCompletedResultSurface() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.startFatMeasurement()
        routeState.lockFatBaseline()
        routeState.recordNextFatReading(12)
        routeState.recordNextFatReading(9)
        routeState.requestFatFinish()
        routeState.completeFatMeasurement(7)

        assertEquals(FatMeasurementFlowStep.COMPLETE, routeState.fatMeasurementFlowState.step)
        assertEquals(
            MeasurementSessionPhase.COMPLETE,
            routeState.fatMeasurementFlowState.activeSession?.phase,
        )
        assertEquals(12, routeState.fatMeasurementFlowState.latestResult?.bestDeltaPercent)
        assertEquals(7, routeState.fatMeasurementFlowState.latestResult?.finalDeltaPercent)
    }

    @Test
    fun fatCancelAndFailurePathsDoNotProduceCompletedSummary() {
        val canceledRouteState = activeRouteState()
        canceledRouteState.openFeature(FeatureKind.FAT_BURNING)
        canceledRouteState.openAction(FeatureAction.MEASURE)
        canceledRouteState.startFatMeasurement()
        canceledRouteState.lockFatBaseline()
        canceledRouteState.cancelFatMeasurement()

        assertEquals(FatMeasurementFlowStep.CANCELED, canceledRouteState.fatMeasurementFlowState.step)
        assertNull(canceledRouteState.fatMeasurementFlowState.latestResult)

        val failedRouteState = activeRouteState()
        failedRouteState.openFeature(FeatureKind.FAT_BURNING)
        failedRouteState.openAction(FeatureAction.MEASURE)
        failedRouteState.startFatMeasurement()
        failedRouteState.lockFatBaseline()
        failedRouteState.failFatMeasurement("invalid_sample")

        assertEquals(FatMeasurementFlowStep.FAILED, failedRouteState.fatMeasurementFlowState.step)
        assertNull(failedRouteState.fatMeasurementFlowState.latestResult)
        assertEquals(
            "invalid_sample",
            failedRouteState.fatMeasurementFlowState.activeSession?.failureReason,
        )
    }

    @Test
    fun replayCoverageCoversOralRecoveryAndFatReplayFailure() {
        val oralRouteState = activeRouteState()
        oralRouteState.openFeature(FeatureKind.ORAL_HEALTH)
        oralRouteState.openAction(FeatureAction.MEASURE)
        oralRouteState.startOralMeasurement()
        oralRouteState.markOralWarmupPassed()
        oralRouteState.disconnectActiveMeasurement()
        oralRouteState.beginReconnectReplay()
        oralRouteState.recoverMeasurementReplay()

        assertEquals(
            MeasurementRecoveryStage.RECOVERED,
            oralRouteState.measurementRecoveryStateFor(FeatureKind.ORAL_HEALTH)?.stage,
        )
        assertEquals(
            MeasurementSessionPhase.COMPLETE,
            oralRouteState.oralMeasurementFlowState.activeSession?.phase,
        )

        val fatRouteState = activeRouteState()
        fatRouteState.openFeature(FeatureKind.FAT_BURNING)
        fatRouteState.openAction(FeatureAction.MEASURE)
        fatRouteState.startFatMeasurement()
        fatRouteState.lockFatBaseline()
        fatRouteState.disconnectActiveMeasurement()
        fatRouteState.beginReconnectReplay()
        fatRouteState.failMeasurementReplay()

        assertEquals(
            MeasurementRecoveryStage.FAILED,
            fatRouteState.measurementRecoveryStateFor(FeatureKind.FAT_BURNING)?.stage,
        )
        assertEquals(
            MeasurementSessionPhase.FAILED,
            fatRouteState.fatMeasurementFlowState.activeSession?.phase,
        )
        assertNull(fatRouteState.fatMeasurementFlowState.latestResult)
    }
}
