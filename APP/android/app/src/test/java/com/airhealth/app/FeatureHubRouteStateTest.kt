package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureHubRouteStateTest {
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
    fun openingFeatureSetsSelectedFeatureContext() {
        val routeState = FeatureHubRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)

        val route = routeState.route
        assertTrue(route is FeatureHubRoute.Feature)
        route as FeatureHubRoute.Feature
        assertEquals(FeatureKind.ORAL_HEALTH, route.context.feature)
        assertEquals("home", route.context.lastVisitedRouteId)
        assertEquals("feature_hub/oral_health", route.routeId)
    }

    @Test
    fun childRouteCanReturnToSelectedFeature() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.MEASURE)

        val actionRoute = routeState.route as FeatureHubRoute.Action
        assertEquals("feature_hub/fat_burning", actionRoute.context.lastVisitedRouteId)
        assertEquals("feature_hub/fat_burning/measure", actionRoute.routeId)

        routeState.returnToFeature()

        val featureRoute = routeState.route as FeatureHubRoute.Feature
        assertEquals(FeatureKind.FAT_BURNING, featureRoute.context.feature)
        assertEquals("feature_hub/fat_burning", featureRoute.routeId)
        assertNull(routeState.activeManagedAction())
    }

    @Test
    fun conflictingActionAttemptKeepsCurrentRouteAndReasonCode() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.openAction(FeatureAction.VIEW_HISTORY)

        val actionRoute = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.MEASURE, actionRoute.action)
        assertEquals(
            ActionLockReasonCode.CONFLICTING_ACTION_IN_PROGRESS,
            routeState.lastBlockedActionAttempt?.reasonCode,
        )
        assertEquals(ManagedAction.MEASURE, routeState.activeManagedAction())
    }

    @Test
    fun resolvingCurrentActionAllowsNextEntryPoint() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.returnToFeature()
        routeState.openAction(FeatureAction.SET_GOALS)

        val actionRoute = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.SET_GOALS, actionRoute.action)
        assertEquals(ManagedAction.SET_GOALS, routeState.activeManagedAction())
        assertNull(routeState.lastBlockedActionAttempt)
    }

    @Test
    fun activeEntitlementAllowsMeasurementFromFeatureHub() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 1_000L })
        routeState.replaceEntitlementCacheState(
            EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                    verifiedAtEpochMillis = 1_000L,
                ),
                isBackendReachable = true,
                lastVerificationAttemptAtEpochMillis = 1_000L,
            ),
        )

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)

        val route = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.MEASURE, route.action)
        assertNull(routeState.lastBlockedActionAttempt)
    }

    @Test
    fun temporaryAccessBlocksMeasurementButKeepsHistoryAvailable() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 2_100L })
        routeState.replaceEntitlementCacheState(
            EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.TRIAL_ACTIVE,
                    verifiedAtEpochMillis = 2_000L,
                ),
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 2_050L,
            ),
        )

        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.MEASURE)

        assertTrue(routeState.route is FeatureHubRoute.Feature)
        assertEquals(
            ActionLockReasonCode.TEMPORARY_ACCESS_RESTRICTION,
            routeState.lastBlockedActionAttempt?.reasonCode,
        )

        routeState.openAction(FeatureAction.VIEW_HISTORY)

        val route = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.VIEW_HISTORY, route.action)
    }

    @Test
    fun verificationFailureWithoutCacheLeavesOnlyReadOnlyRoutesAvailable() {
        val routeState = FeatureHubRouteState(currentTimeMillis = { 5_000L })
        routeState.replaceEntitlementCacheState(
            EntitlementCacheState(
                snapshot = null,
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 4_900L,
            ),
        )

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.GET_SUGGESTION)

        assertTrue(routeState.route is FeatureHubRoute.Feature)
        assertEquals(
            ActionLockReasonCode.READ_ONLY_MODE_RESTRICTION,
            routeState.lastBlockedActionAttempt?.reasonCode,
        )
        assertFalse(routeState.effectiveEntitlement.canRequestLiveSuggestions)

        routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)

        val route = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.CONSULT_PROFESSIONALS, route.action)
    }

    @Test
    fun completedSummaryCanMoveFromPendingToSyncedThroughQueueActions() {
        val routeState = activeRouteState()

        routeState.recordDemoCompletedSession(FeatureKind.ORAL_HEALTH)

        val pendingHistory = routeState.historyProjectionFor(FeatureKind.ORAL_HEALTH)
        val pendingQueue = routeState.syncQueueProjectionFor(FeatureKind.ORAL_HEALTH)
        assertEquals(1, pendingHistory.pendingCount)
        assertEquals(1, pendingQueue.pendingCount)

        val activeJob = routeState.beginNextSyncAttempt()
        assertNotNull(activeJob)
        assertEquals(
            1,
            routeState.syncQueueProjectionFor(FeatureKind.ORAL_HEALTH).inFlightCount,
        )

        routeState.markSyncAttemptSucceeded(activeJob!!.sessionId)

        val syncedHistory = routeState.historyProjectionFor(FeatureKind.ORAL_HEALTH)
        val syncedQueue = routeState.syncQueueProjectionFor(FeatureKind.ORAL_HEALTH)
        assertEquals(0, syncedHistory.pendingCount)
        assertEquals(1, syncedHistory.syncedCount)
        assertEquals(1, syncedQueue.syncedCount)
    }

    @Test
    fun oralMeasurementRouteSurfacesGuidedCompletionAndBaselineProgress() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.startOralMeasurement()
        routeState.markOralWarmupPassed()
        routeState.completeOralMeasurement()

        assertEquals(OralMeasurementFlowStep.COMPLETE, routeState.oralMeasurementFlowState.step)
        assertEquals(
            "1/5 baseline sessions",
            routeState.oralMeasurementFlowState.latestResult?.baselineProgressLabel,
        )
        assertEquals(
            MeasurementSessionPhase.COMPLETE,
            routeState.oralMeasurementFlowState.activeSession?.phase,
        )
    }

    @Test
    fun fatMeasurementRouteKeepsBestAndFinalDeltaDistinct() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.startFatMeasurement()
        routeState.lockFatBaseline()
        routeState.recordNextFatReading(11)
        routeState.recordNextFatReading(8)
        routeState.requestFatFinish()
        routeState.completeFatMeasurement(6)

        assertEquals(FatMeasurementFlowStep.COMPLETE, routeState.fatMeasurementFlowState.step)
        assertEquals(11, routeState.fatMeasurementFlowState.bestDeltaPercent)
        assertEquals(6, routeState.fatMeasurementFlowState.currentDeltaPercent)
        assertEquals(
            "Best Fat Burn Delta +11%",
            routeState.fatMeasurementFlowState.latestResult?.bestDeltaLabel,
        )
        assertEquals(
            "Final Fat Burn Delta +6%",
            routeState.fatMeasurementFlowState.latestResult?.finalDeltaLabel,
        )
    }

    @Test
    fun reconnectReplayCanRecoverCompletedOralResultAfterDisconnect() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.startOralMeasurement()
        routeState.markOralWarmupPassed()
        routeState.disconnectActiveMeasurement()

        assertEquals(MeasurementRecoveryStage.INTERRUPTED, routeState.measurementRecoveryStateFor(FeatureKind.ORAL_HEALTH)?.stage)
        assertEquals(MeasurementSessionPhase.PAUSED, routeState.oralMeasurementFlowState.activeSession?.phase)

        routeState.beginReconnectReplay()
        routeState.recoverMeasurementReplay()

        assertEquals(MeasurementRecoveryStage.RECOVERED, routeState.measurementRecoveryStateFor(FeatureKind.ORAL_HEALTH)?.stage)
        assertEquals(MeasurementSessionPhase.COMPLETE, routeState.oralMeasurementFlowState.activeSession?.phase)
        assertTrue(routeState.oralMeasurementFlowState.recoveryMessage!!.contains("Recovered"))
    }

    @Test
    fun replayFailureLeavesFatSessionFailedWithoutCompletedSummary() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.startFatMeasurement()
        routeState.lockFatBaseline()
        routeState.disconnectActiveMeasurement()
        routeState.beginReconnectReplay()
        routeState.failMeasurementReplay()

        assertEquals(MeasurementRecoveryStage.FAILED, routeState.measurementRecoveryStateFor(FeatureKind.FAT_BURNING)?.stage)
        assertEquals(MeasurementSessionPhase.FAILED, routeState.fatMeasurementFlowState.activeSession?.phase)
        assertEquals("replay_unavailable", routeState.fatMeasurementFlowState.activeSession?.failureReason)
        assertNull(routeState.fatMeasurementFlowState.latestResult)
    }

    @Test
    fun consultDirectoryCacheCanBeLoadedAndReopenedPerFeature() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)
        routeState.refreshConsultDirectory(FeatureKind.ORAL_HEALTH)

        val oralDirectory = requireNotNull(routeState.consultDirectoryFor(FeatureKind.ORAL_HEALTH))
        assertEquals("en-US", oralDirectory.localeTag)
        assertEquals(1, oralDirectory.refreshRevision)
        assertEquals(2, oralDirectory.resources.size)

        routeState.returnToFeature()
        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)
        routeState.refreshConsultDirectory(FeatureKind.FAT_BURNING)

        val fatDirectory = requireNotNull(routeState.consultDirectoryFor(FeatureKind.FAT_BURNING))
        assertEquals("Metabolic Performance Coach", fatDirectory.resources.first().title)
        assertEquals("AirHealth Oral Wellness Coach", oralDirectory.resources.first().title)
    }

    @Test
    fun consultHandoffRequiresExplicitConfirmationBeforeLaunch() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.ORAL_HEALTH)
        routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)
        routeState.refreshConsultDirectory(FeatureKind.ORAL_HEALTH)

        val resourceTitle = requireNotNull(
            routeState.consultDirectoryFor(FeatureKind.ORAL_HEALTH)
                ?.resources
                ?.first()
                ?.title,
        )

        routeState.beginConsultHandoff(
            feature = FeatureKind.ORAL_HEALTH,
            resourceTitle = resourceTitle,
        )

        assertEquals(resourceTitle, routeState.pendingConsultHandoff?.resource?.title)

        val launchedUrl = routeState.confirmConsultHandoff()

        assertEquals("https://care.airhealth.app/oral-wellness-coach", launchedUrl)
        assertNull(routeState.pendingConsultHandoff)
        assertEquals(
            "AirHealth Oral Wellness Coach",
            routeState.consultHandoffAnalytics.events.last().resourceTitle,
        )
    }

    @Test
    fun consultEntryRemainsAvailableAcrossAllowedEntitlementStates() {
        val entitlementStates = listOf(
            EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                    verifiedAtEpochMillis = 1_000L,
                ),
                isBackendReachable = true,
                lastVerificationAttemptAtEpochMillis = 1_000L,
            ),
            EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.TRIAL_ACTIVE,
                    verifiedAtEpochMillis = 1_000L,
                ),
                isBackendReachable = true,
                lastVerificationAttemptAtEpochMillis = 1_000L,
            ),
            EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                    verifiedAtEpochMillis = 1_000L,
                ),
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 2_000L,
            ),
            EntitlementCacheState(
                snapshot = null,
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 2_000L,
            ),
        )

        entitlementStates.forEach { entitlementState ->
            val routeState = FeatureHubRouteState(currentTimeMillis = { 3_000L })
            routeState.replaceEntitlementCacheState(entitlementState)

            routeState.openFeature(FeatureKind.ORAL_HEALTH)
            routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)

            val route = routeState.route as FeatureHubRoute.Action
            assertEquals(FeatureAction.CONSULT_PROFESSIONALS, route.action)
            assertNull(routeState.lastBlockedActionAttempt)
        }
    }

    @Test
    fun conflictingActionStillBlocksConsultEntryWithExplicitReason() {
        val routeState = activeRouteState()

        routeState.openFeature(FeatureKind.FAT_BURNING)
        routeState.openAction(FeatureAction.MEASURE)
        routeState.openAction(FeatureAction.CONSULT_PROFESSIONALS)

        val actionRoute = routeState.route as FeatureHubRoute.Action
        assertEquals(FeatureAction.MEASURE, actionRoute.action)
        assertEquals(
            ActionLockReasonCode.CONFLICTING_ACTION_IN_PROGRESS,
            routeState.lastBlockedActionAttempt?.reasonCode,
        )
        assertEquals(
            ManagedAction.CONSULT_PROFESSIONALS,
            routeState.lastBlockedActionAttempt?.requestedAction,
        )
    }
}
