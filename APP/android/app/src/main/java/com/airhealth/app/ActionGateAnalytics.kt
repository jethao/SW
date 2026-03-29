package com.airhealth.app

enum class ActionGateOutcome(
    val code: String,
) {
    ALLOWED("allowed"),
    BLOCKED("blocked"),
}

data class ActionGateEvent(
    val feature: String,
    val requestedAction: String,
    val outcome: String,
    val activeAction: String? = null,
    val reasonCode: String? = null,
) {
    fun payload(): Map<String, String> {
        val payload = linkedMapOf(
            "feature" to feature,
            "requested_action" to requestedAction,
            "outcome" to outcome,
        )

        activeAction?.let { payload["active_action"] = it }
        reasonCode?.let { payload["reason_code"] = it }
        return payload
    }
}

class ActionGateAnalytics {
    private val recordedEvents = mutableListOf<ActionGateEvent>()

    val events: List<ActionGateEvent>
        get() = recordedEvents

    fun recordAllowed(
        feature: FeatureKind,
        requestedAction: ManagedAction,
    ) {
        recordedEvents += ActionGateEvent(
            feature = feature.routeId,
            requestedAction = requestedAction.routeId,
            outcome = ActionGateOutcome.ALLOWED.code,
        )
    }

    fun recordBlocked(blockedAttempt: BlockedActionAttempt) {
        recordedEvents += ActionGateEvent(
            feature = blockedAttempt.requestedFeature.routeId,
            requestedAction = blockedAttempt.requestedAction.routeId,
            outcome = ActionGateOutcome.BLOCKED.code,
            activeAction = blockedAttempt.activeAction.routeId,
            reasonCode = blockedAttempt.reasonCode.code,
        )
    }
}
