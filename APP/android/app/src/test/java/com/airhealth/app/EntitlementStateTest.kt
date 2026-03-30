package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementStateTest {
    @Test
    fun reducerStoresVerifiedSnapshotAndRestoresReachability() {
        val verifiedSnapshot = CachedEntitlementSnapshot(
            sourceState = VerifiedEntitlementState.TRIAL_ACTIVE,
            verifiedAtEpochMillis = 1_000L,
        )

        val verifiedState = EntitlementCacheReducer.reduce(
            EntitlementCacheState(
                snapshot = null,
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 900L,
            ),
            EntitlementCacheAction.SnapshotVerified(verifiedSnapshot),
        )

        assertEquals(verifiedSnapshot, verifiedState.snapshot)
        assertTrue(verifiedState.isBackendReachable)
        assertEquals(1_000L, verifiedState.lastVerificationAttemptAtEpochMillis)

        val unavailableState = EntitlementCacheReducer.reduce(
            verifiedState,
            EntitlementCacheAction.VerificationUnavailable(observedAtEpochMillis = 2_000L),
        )

        assertFalse(unavailableState.isBackendReachable)
        assertEquals(2_000L, unavailableState.lastVerificationAttemptAtEpochMillis)
        assertEquals(verifiedSnapshot, unavailableState.snapshot)
    }

    @Test
    fun selectorTreatsVerifiedActiveSnapshotAsActive() {
        val state = EntitlementCacheState(
            snapshot = CachedEntitlementSnapshot(
                sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                verifiedAtEpochMillis = 100L,
            ),
            isBackendReachable = true,
        )

        val effective = EntitlementEvaluator.deriveEffectiveState(
            state = state,
            nowEpochMillis = 5_000L,
        )

        assertEquals(EffectiveEntitlementMode.ACTIVE, effective.mode)
        assertEquals(EntitlementFreshness.VERIFIED, effective.freshness)
        assertTrue(effective.canStartNewSessions)
        assertTrue(effective.canEditGoals)
        assertTrue(effective.canRequestLiveSuggestions)
    }

    @Test
    fun selectorTreatsReachableButStaleActiveSnapshotAsReadOnly() {
        val state = EntitlementCacheState(
            snapshot = CachedEntitlementSnapshot(
                sourceState = VerifiedEntitlementState.TRIAL_ACTIVE,
                verifiedAtEpochMillis = 100L,
            ),
            isBackendReachable = true,
        )

        val effective = EntitlementEvaluator.deriveEffectiveState(
            state = state,
            nowEpochMillis = 100L + (24L * 60L * 60L * 1000L) + 1L,
        )

        assertEquals(EffectiveEntitlementMode.READ_ONLY, effective.mode)
        assertEquals(EntitlementFreshness.STALE_CACHE, effective.freshness)
        assertFalse(effective.canStartNewSessions)
        assertFalse(effective.canEditGoals)
        assertFalse(effective.canRequestLiveSuggestions)
    }

    @Test
    fun selectorKeepsActiveAccessAtFreshnessBoundaryThenTransitionsToReadOnly() {
        val verifiedAt = 100L
        val state = EntitlementCacheState(
            snapshot = CachedEntitlementSnapshot(
                sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                verifiedAtEpochMillis = verifiedAt,
            ),
            isBackendReachable = true,
        )

        val boundaryEffective = EntitlementEvaluator.deriveEffectiveState(
            state = state,
            nowEpochMillis = verifiedAt + (24L * 60L * 60L * 1000L),
        )
        assertEquals(EffectiveEntitlementMode.ACTIVE, boundaryEffective.mode)
        assertEquals(EntitlementFreshness.VERIFIED, boundaryEffective.freshness)

        val staleEffective = EntitlementEvaluator.deriveEffectiveState(
            state = state,
            nowEpochMillis = verifiedAt + (24L * 60L * 60L * 1000L) + 1L,
        )
        assertEquals(EffectiveEntitlementMode.READ_ONLY, staleEffective.mode)
        assertEquals(EntitlementFreshness.STALE_CACHE, staleEffective.freshness)
    }

    @Test
    fun selectorTreatsFreshOfflineActiveSnapshotAsTemporaryAccess() {
        val state = EntitlementCacheState(
            snapshot = CachedEntitlementSnapshot(
                sourceState = VerifiedEntitlementState.TRIAL_ACTIVE,
                verifiedAtEpochMillis = 10_000L,
            ),
            isBackendReachable = false,
        )

        val effective = EntitlementEvaluator.deriveEffectiveState(
            state = state,
            nowEpochMillis = 10_000L + (24L * 60L * 60L * 1000L) - 1L,
        )

        assertEquals(EffectiveEntitlementMode.TEMPORARY_ACCESS, effective.mode)
        assertEquals(EntitlementFreshness.FRESH_CACHE, effective.freshness)
        assertFalse(effective.canStartNewSessions)
        assertFalse(effective.canEditGoals)
        assertFalse(effective.canRequestLiveSuggestions)
        assertTrue(effective.canUseCachedSuggestions)
        assertTrue(effective.canViewHistory)
    }

    @Test
    fun selectorTreatsStaleAndInactiveOfflineSnapshotsAsReadOnly() {
        val staleActiveState = EntitlementCacheState(
            snapshot = CachedEntitlementSnapshot(
                sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                verifiedAtEpochMillis = 50L,
            ),
            isBackendReachable = false,
        )
        val staleEffective = EntitlementEvaluator.deriveEffectiveState(
            state = staleActiveState,
            nowEpochMillis = 50L + (24L * 60L * 60L * 1000L) + 1L,
        )
        assertEquals(EffectiveEntitlementMode.READ_ONLY, staleEffective.mode)
        assertEquals(EntitlementFreshness.STALE_CACHE, staleEffective.freshness)

        val inactiveState = EntitlementCacheState(
            snapshot = CachedEntitlementSnapshot(
                sourceState = VerifiedEntitlementState.EXPIRED,
                verifiedAtEpochMillis = 500L,
            ),
            isBackendReachable = false,
        )
        val inactiveEffective = EntitlementEvaluator.deriveEffectiveState(
            state = inactiveState,
            nowEpochMillis = 1_000L,
        )
        assertEquals(EffectiveEntitlementMode.READ_ONLY, inactiveEffective.mode)
        assertEquals(EntitlementFreshness.INACTIVE_CACHE, inactiveEffective.freshness)
        assertFalse(inactiveEffective.canStartNewSessions)
        assertFalse(inactiveEffective.canEditGoals)
        assertFalse(inactiveEffective.canRequestLiveSuggestions)
        assertTrue(inactiveEffective.canUseCachedSuggestions)
    }

    @Test
    fun selectorTreatsVerifiedExpiredSnapshotAsReadOnly() {
        val state = EntitlementCacheState(
            snapshot = CachedEntitlementSnapshot(
                sourceState = VerifiedEntitlementState.EXPIRED,
                verifiedAtEpochMillis = 5_000L,
            ),
            isBackendReachable = true,
        )

        val effective = EntitlementEvaluator.deriveEffectiveState(
            state = state,
            nowEpochMillis = 6_000L,
        )

        assertEquals(EffectiveEntitlementMode.READ_ONLY, effective.mode)
        assertEquals(EntitlementFreshness.VERIFIED, effective.freshness)
        assertFalse(effective.canStartNewSessions)
        assertFalse(effective.canEditGoals)
        assertFalse(effective.canRequestLiveSuggestions)
    }

    @Test
    fun selectorTreatsMissingSnapshotAsReadOnly() {
        val effective = EntitlementEvaluator.deriveEffectiveState(
            state = EntitlementCacheState(
                snapshot = null,
                isBackendReachable = false,
            ),
            nowEpochMillis = 123L,
        )

        assertEquals(EffectiveEntitlementMode.READ_ONLY, effective.mode)
        assertEquals(EntitlementFreshness.MISSING_CACHE, effective.freshness)
        assertFalse(effective.canStartNewSessions)
        assertFalse(effective.canEditGoals)
        assertFalse(effective.canRequestLiveSuggestions)
        assertTrue(effective.canUseCachedSuggestions)
        assertTrue(effective.canViewHistory)
    }

    @Test
    fun cacheClearedPreservesObservedVerificationFailureContext() {
        val reduced = EntitlementCacheReducer.reduce(
            state = EntitlementCacheState(
                snapshot = CachedEntitlementSnapshot(
                    sourceState = VerifiedEntitlementState.PAID_ACTIVE,
                    verifiedAtEpochMillis = 1_000L,
                ),
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = 1_500L,
            ),
            action = EntitlementCacheAction.CacheCleared(observedAtEpochMillis = 2_000L),
        )

        assertEquals(null, reduced.snapshot)
        assertFalse(reduced.isBackendReachable)
        assertEquals(2_000L, reduced.lastVerificationAttemptAtEpochMillis)
    }
}
