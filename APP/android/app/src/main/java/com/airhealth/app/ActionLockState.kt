package com.airhealth.app

enum class ManagedAction(
    val routeId: String,
    val title: String,
) {
    SETUP("setup", "Setup"),
    SET_GOALS("set_goals", "Set Goals"),
    VIEW_HISTORY("view_history", "View History"),
    MEASURE("measure", "Measure"),
    GET_SUGGESTION("get_suggestion", "Get Suggestion"),
    CONSULT_PROFESSIONALS("consult_professionals", "Consult Professionals"),
    ;

    companion object {
        fun fromFeatureAction(action: FeatureAction): ManagedAction {
            return when (action) {
                FeatureAction.SET_GOALS -> SET_GOALS
                FeatureAction.VIEW_HISTORY -> VIEW_HISTORY
                FeatureAction.MEASURE -> MEASURE
                FeatureAction.GET_SUGGESTION -> GET_SUGGESTION
                FeatureAction.CONSULT_PROFESSIONALS -> CONSULT_PROFESSIONALS
            }
        }
    }
}

enum class ActionLockReasonCode(
    val code: String,
) {
    CONFLICTING_ACTION_IN_PROGRESS("conflicting_action_in_progress"),
}

data class BlockedActionAttempt(
    val requestedFeature: FeatureKind,
    val requestedAction: ManagedAction,
    val activeFeature: FeatureKind,
    val activeAction: ManagedAction,
    val reasonCode: ActionLockReasonCode,
) {
    val message: String
        get() = "Finish ${activeAction.title} for ${activeFeature.title} before starting ${requestedAction.title}."
}

data class ActionLockState(
    val activeFeature: FeatureKind? = null,
    val activeAction: ManagedAction? = null,
    val blockedAttempt: BlockedActionAttempt? = null,
) {
    fun tryAcquire(
        feature: FeatureKind,
        action: ManagedAction,
    ): ActionLockState {
        val currentAction = activeAction
        val currentFeature = activeFeature

        if (currentAction == null || currentFeature == null) {
            return copy(
                activeFeature = feature,
                activeAction = action,
                blockedAttempt = null,
            )
        }

        if (currentAction == action && currentFeature == feature) {
            return copy(blockedAttempt = null)
        }

        return copy(
            blockedAttempt = BlockedActionAttempt(
                requestedFeature = feature,
                requestedAction = action,
                activeFeature = currentFeature,
                activeAction = currentAction,
                reasonCode = ActionLockReasonCode.CONFLICTING_ACTION_IN_PROGRESS,
            ),
        )
    }

    fun release(): ActionLockState {
        return ActionLockState()
    }

    fun clearBlockedAttempt(): ActionLockState {
        return copy(blockedAttempt = null)
    }
}
