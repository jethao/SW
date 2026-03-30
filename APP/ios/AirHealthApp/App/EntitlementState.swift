import Foundation

private let entitlementFreshnessWindow: TimeInterval = 24 * 60 * 60

enum VerifiedEntitlementState: String {
    case trialActive = "trial_active"
    case paidActive = "paid_active"
    case expiredReadOnly = "expired_read_only"
}

enum EntitlementFreshness: String {
    case verified = "verified"
    case freshCache = "fresh_cache"
    case staleCache = "stale_cache"
    case inactiveCache = "inactive_cache"
    case missingCache = "missing_cache"
}

enum EffectiveEntitlementMode: String {
    case active = "active"
    case temporaryAccess = "temporary_access"
    case readOnly = "read_only"
}

struct CachedEntitlementSnapshot {
    let sourceState: VerifiedEntitlementState
    let verifiedAtEpochMillis: Int64
}

struct EntitlementCacheState {
    let snapshot: CachedEntitlementSnapshot?
    let isBackendReachable: Bool
    let lastVerificationAttemptAtEpochMillis: Int64?

    static func empty() -> EntitlementCacheState {
        EntitlementCacheState(
            snapshot: nil,
            isBackendReachable: true,
            lastVerificationAttemptAtEpochMillis: nil
        )
    }
}

enum EntitlementCacheAction {
    case snapshotVerified(CachedEntitlementSnapshot)
    case verificationUnavailable(observedAtEpochMillis: Int64)
    case cacheCleared(observedAtEpochMillis: Int64)
}

struct EffectiveEntitlement {
    let mode: EffectiveEntitlementMode
    let freshness: EntitlementFreshness
    let canStartNewSessions: Bool
    let canViewHistory: Bool
    let canEditGoals: Bool
    let canRequestLiveSuggestions: Bool
    let canUseCachedSuggestions: Bool
}

enum EntitlementCacheReducer {
    static func reduce(
        state: EntitlementCacheState,
        action: EntitlementCacheAction
    ) -> EntitlementCacheState {
        switch action {
        case let .snapshotVerified(snapshot):
            return EntitlementCacheState(
                snapshot: snapshot,
                isBackendReachable: true,
                lastVerificationAttemptAtEpochMillis: snapshot.verifiedAtEpochMillis
            )
        case let .verificationUnavailable(observedAtEpochMillis):
            return EntitlementCacheState(
                snapshot: state.snapshot,
                isBackendReachable: false,
                lastVerificationAttemptAtEpochMillis: observedAtEpochMillis
            )
        case let .cacheCleared(observedAtEpochMillis):
            return EntitlementCacheState(
                snapshot: nil,
                isBackendReachable: state.isBackendReachable,
                lastVerificationAttemptAtEpochMillis: observedAtEpochMillis
            )
        }
    }
}

enum EntitlementEvaluator {
    static func deriveEffectiveState(
        state: EntitlementCacheState,
        nowEpochMillis: Int64
    ) -> EffectiveEntitlement {
        guard let snapshot = state.snapshot else {
            return readOnlyEntitlement(freshness: .missingCache)
        }

        if state.isBackendReachable {
            switch snapshot.sourceState {
            case .trialActive, .paidActive:
                return activeEntitlement(freshness: .verified)
            case .expiredReadOnly:
                return readOnlyEntitlement(freshness: .verified)
            }
        }

        let cacheAgeMillis = max(0, nowEpochMillis - snapshot.verifiedAtEpochMillis)
        let hasFreshActiveCache =
            snapshot.sourceState != .expiredReadOnly &&
            TimeInterval(cacheAgeMillis) <= entitlementFreshnessWindow * 1000

        if hasFreshActiveCache {
            return EffectiveEntitlement(
                mode: .temporaryAccess,
                freshness: .freshCache,
                canStartNewSessions: false,
                canViewHistory: true,
                canEditGoals: false,
                canRequestLiveSuggestions: false,
                canUseCachedSuggestions: true
            )
        }

        if snapshot.sourceState == .expiredReadOnly {
            return readOnlyEntitlement(freshness: .inactiveCache)
        }

        return readOnlyEntitlement(freshness: .staleCache)
    }

    private static func activeEntitlement(
        freshness: EntitlementFreshness
    ) -> EffectiveEntitlement {
        EffectiveEntitlement(
            mode: .active,
            freshness: freshness,
            canStartNewSessions: true,
            canViewHistory: true,
            canEditGoals: true,
            canRequestLiveSuggestions: true,
            canUseCachedSuggestions: true
        )
    }

    private static func readOnlyEntitlement(
        freshness: EntitlementFreshness
    ) -> EffectiveEntitlement {
        EffectiveEntitlement(
            mode: .readOnly,
            freshness: freshness,
            canStartNewSessions: false,
            canViewHistory: true,
            canEditGoals: false,
            canRequestLiveSuggestions: false,
            canUseCachedSuggestions: true
        )
    }
}
