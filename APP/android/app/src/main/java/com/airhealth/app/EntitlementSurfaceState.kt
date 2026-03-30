package com.airhealth.app

data class EntitlementBannerState(
    val title: String,
    val message: String,
)

data class FeatureActionSurfaceState(
    val isEnabled: Boolean,
    val detail: String? = null,
)

fun entitlementBannerState(entitlement: EffectiveEntitlement): EntitlementBannerState? {
    return when (entitlement.mode) {
        EffectiveEntitlementMode.ACTIVE -> null
        EffectiveEntitlementMode.TEMPORARY_ACCESS -> EntitlementBannerState(
            title = "Temporary access",
            message = "Verification is temporarily unavailable. You can still view history and consult resources, but new sessions, goal edits, and live suggestions stay paused until verification recovers.",
        )

        EffectiveEntitlementMode.READ_ONLY -> {
            val message = when (entitlement.freshness) {
                EntitlementFreshness.STALE_CACHE ->
                    "Your last verified access is stale. History and consult resources remain available, but measurement, goal changes, and live suggestions stay locked until entitlement is verified again."

                EntitlementFreshness.INACTIVE_CACHE,
                EntitlementFreshness.VERIFIED,
                EntitlementFreshness.MISSING_CACHE,
                EntitlementFreshness.FRESH_CACHE,
                ->
                    "Your access is currently read-only. History and consult resources remain available, but measurement, goal changes, and live suggestions stay locked until active entitlement returns."
            }

            EntitlementBannerState(
                title = "Read-only mode",
                message = message,
            )
        }
    }
}

fun featureActionSurfaceState(
    action: FeatureAction,
    entitlement: EffectiveEntitlement,
): FeatureActionSurfaceState {
    if (entitlement.mode == EffectiveEntitlementMode.ACTIVE) {
        return FeatureActionSurfaceState(isEnabled = true)
    }

    val isEnabled = when (action) {
        FeatureAction.MEASURE -> entitlement.canStartNewSessions
        FeatureAction.SET_GOALS -> entitlement.canEditGoals
        FeatureAction.GET_SUGGESTION -> entitlement.canRequestLiveSuggestions
        FeatureAction.VIEW_HISTORY,
        FeatureAction.CONSULT_PROFESSIONALS,
        -> true
    }

    if (isEnabled) {
        return FeatureActionSurfaceState(isEnabled = true)
    }

    val actionTitle = action.title
    val detail = when (entitlement.mode) {
        EffectiveEntitlementMode.ACTIVE -> null
        EffectiveEntitlementMode.TEMPORARY_ACCESS ->
            "$actionTitle is unavailable during Temporary access. Verify entitlement again to continue."

        EffectiveEntitlementMode.READ_ONLY ->
            "$actionTitle is unavailable in Read-only mode. Restore active entitlement to continue."
    }

    return FeatureActionSurfaceState(
        isEnabled = false,
        detail = detail,
    )
}
