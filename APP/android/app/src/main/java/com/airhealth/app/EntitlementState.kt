package com.airhealth.app

private const val ENTITLEMENT_FRESHNESS_WINDOW_MS = 24L * 60L * 60L * 1000L

enum class VerifiedEntitlementState {
    TRIAL_ACTIVE,
    PAID_ACTIVE,
    EXPIRED,
}

enum class EntitlementFreshness {
    VERIFIED,
    FRESH_CACHE,
    STALE_CACHE,
    INACTIVE_CACHE,
    MISSING_CACHE,
}

enum class EffectiveEntitlementMode {
    ACTIVE,
    TEMPORARY_ACCESS,
    READ_ONLY,
}

data class CachedEntitlementSnapshot(
    val sourceState: VerifiedEntitlementState,
    val verifiedAtEpochMillis: Long,
)

data class EntitlementCacheState(
    val snapshot: CachedEntitlementSnapshot? = null,
    val isBackendReachable: Boolean = true,
    val lastVerificationAttemptAtEpochMillis: Long? = null,
)

sealed interface EntitlementCacheAction {
    data class SnapshotVerified(
        val snapshot: CachedEntitlementSnapshot,
    ) : EntitlementCacheAction

    data class VerificationUnavailable(
        val observedAtEpochMillis: Long,
    ) : EntitlementCacheAction

    data class CacheCleared(
        val observedAtEpochMillis: Long,
    ) : EntitlementCacheAction
}

data class EffectiveEntitlement(
    val mode: EffectiveEntitlementMode,
    val freshness: EntitlementFreshness,
    val canStartNewSessions: Boolean,
    val canViewHistory: Boolean,
    val canEditGoals: Boolean,
    val canRequestLiveSuggestions: Boolean,
    val canUseCachedSuggestions: Boolean,
)

object EntitlementCacheReducer {
    fun reduce(
        state: EntitlementCacheState,
        action: EntitlementCacheAction,
    ): EntitlementCacheState {
        return when (action) {
            is EntitlementCacheAction.SnapshotVerified -> state.copy(
                snapshot = action.snapshot,
                isBackendReachable = true,
                lastVerificationAttemptAtEpochMillis = action.snapshot.verifiedAtEpochMillis,
            )

            is EntitlementCacheAction.VerificationUnavailable -> state.copy(
                isBackendReachable = false,
                lastVerificationAttemptAtEpochMillis = action.observedAtEpochMillis,
            )

            is EntitlementCacheAction.CacheCleared -> EntitlementCacheState(
                snapshot = null,
                isBackendReachable = state.isBackendReachable,
                lastVerificationAttemptAtEpochMillis = action.observedAtEpochMillis,
            )
        }
    }
}

object EntitlementEvaluator {
    fun deriveEffectiveState(
        state: EntitlementCacheState,
        nowEpochMillis: Long,
    ): EffectiveEntitlement {
        val snapshot = state.snapshot
            ?: return readOnlyEntitlement(EntitlementFreshness.MISSING_CACHE)

        val cacheAgeMillis = (nowEpochMillis - snapshot.verifiedAtEpochMillis).coerceAtLeast(0L)
        val isWithinFreshnessWindow = cacheAgeMillis <= ENTITLEMENT_FRESHNESS_WINDOW_MS
        val isActiveSourceState = snapshot.sourceState == VerifiedEntitlementState.TRIAL_ACTIVE ||
            snapshot.sourceState == VerifiedEntitlementState.PAID_ACTIVE

        if (state.isBackendReachable) {
            return when (snapshot.sourceState) {
                VerifiedEntitlementState.TRIAL_ACTIVE,
                VerifiedEntitlementState.PAID_ACTIVE,
                -> if (isWithinFreshnessWindow) {
                    activeEntitlement(EntitlementFreshness.VERIFIED)
                } else {
                    readOnlyEntitlement(EntitlementFreshness.STALE_CACHE)
                }

                VerifiedEntitlementState.EXPIRED -> if (isWithinFreshnessWindow) {
                    readOnlyEntitlement(EntitlementFreshness.VERIFIED)
                } else {
                    readOnlyEntitlement(EntitlementFreshness.INACTIVE_CACHE)
                }
            }
        }

        val hasFreshActiveCache = isActiveSourceState && isWithinFreshnessWindow

        return when {
            hasFreshActiveCache -> EffectiveEntitlement(
                mode = EffectiveEntitlementMode.TEMPORARY_ACCESS,
                freshness = EntitlementFreshness.FRESH_CACHE,
                canStartNewSessions = false,
                canViewHistory = true,
                canEditGoals = false,
                canRequestLiveSuggestions = false,
                canUseCachedSuggestions = true,
            )

            snapshot.sourceState == VerifiedEntitlementState.EXPIRED ->
                readOnlyEntitlement(EntitlementFreshness.INACTIVE_CACHE)

            else -> readOnlyEntitlement(EntitlementFreshness.STALE_CACHE)
        }
    }

    private fun activeEntitlement(freshness: EntitlementFreshness): EffectiveEntitlement {
        return EffectiveEntitlement(
            mode = EffectiveEntitlementMode.ACTIVE,
            freshness = freshness,
            canStartNewSessions = true,
            canViewHistory = true,
            canEditGoals = true,
            canRequestLiveSuggestions = true,
            canUseCachedSuggestions = true,
        )
    }

    private fun readOnlyEntitlement(freshness: EntitlementFreshness): EffectiveEntitlement {
        return EffectiveEntitlement(
            mode = EffectiveEntitlementMode.READ_ONLY,
            freshness = freshness,
            canStartNewSessions = false,
            canViewHistory = true,
            canEditGoals = false,
            canRequestLiveSuggestions = false,
            canUseCachedSuggestions = true,
        )
    }
}
