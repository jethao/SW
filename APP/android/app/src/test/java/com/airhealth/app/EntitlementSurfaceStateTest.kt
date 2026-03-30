package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementSurfaceStateTest {
    @Test
    fun activeEntitlementHasNoBannerAndLeavesActionsEnabled() {
        val effective = EntitlementEvaluator.deriveEffectiveState(
            state = scaffoldBootstrapEntitlementState(nowEpochMillis = 100L),
            nowEpochMillis = 200L,
        )

        assertNull(entitlementBannerState(effective))
        assertTrue(featureActionSurfaceState(FeatureAction.MEASURE, effective).isEnabled)
        assertTrue(featureActionSurfaceState(FeatureAction.SET_GOALS, effective).isEnabled)
        assertTrue(featureActionSurfaceState(FeatureAction.GET_SUGGESTION, effective).isEnabled)
    }

    @Test
    fun temporaryAccessShowsBannerAndDisablesVerificationDependentActions() {
        val effective = EntitlementEvaluator.deriveEffectiveState(
            state = EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                    verifiedAtEpochMillis = 1_000L,
                ),
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 2_000L,
            ),
            nowEpochMillis = 2_100L,
        )

        val banner = entitlementBannerState(effective)
        assertNotNull(banner)
        assertEquals("Temporary access", banner?.title)

        val measureState = featureActionSurfaceState(FeatureAction.MEASURE, effective)
        assertFalse(measureState.isEnabled)
        assertTrue(measureState.detail?.contains("Temporary access") == true)
        assertTrue(featureActionSurfaceState(FeatureAction.VIEW_HISTORY, effective).isEnabled)
    }

    @Test
    fun staleReadOnlyShowsBannerAndKeepsHistoryAvailable() {
        val effective = EntitlementEvaluator.deriveEffectiveState(
            state = EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.TRIAL_ACTIVE,
                    verifiedAtEpochMillis = 100L,
                ),
                isBackendReachable = true,
                lastVerificationAttemptAtEpochMillis = 100L,
            ),
            nowEpochMillis = 100L + (24L * 60L * 60L * 1000L) + 10L,
        )

        val banner = entitlementBannerState(effective)
        assertNotNull(banner)
        assertEquals("Read-only mode", banner?.title)
        assertTrue(banner?.message?.contains("stale") == true)

        val suggestionState = featureActionSurfaceState(FeatureAction.GET_SUGGESTION, effective)
        assertFalse(suggestionState.isEnabled)
        assertTrue(suggestionState.detail?.contains("Read-only mode") == true)
        assertTrue(featureActionSurfaceState(FeatureAction.CONSULT_PROFESSIONALS, effective).isEnabled)
    }

    @Test
    fun missingCacheShowsReadOnlyBannerAndBlocksLiveOnlyActions() {
        val effective = EntitlementEvaluator.deriveEffectiveState(
            state = EntitlementCacheState(
                snapshot = null,
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 9_000L,
            ),
            nowEpochMillis = 9_100L,
        )

        val banner = entitlementBannerState(effective)
        assertNotNull(banner)
        assertEquals("Read-only mode", banner?.title)

        val measureState = featureActionSurfaceState(FeatureAction.MEASURE, effective)
        assertFalse(measureState.isEnabled)
        assertTrue(measureState.detail?.contains("Read-only mode") == true)
        assertTrue(featureActionSurfaceState(FeatureAction.VIEW_HISTORY, effective).isEnabled)
    }
}
