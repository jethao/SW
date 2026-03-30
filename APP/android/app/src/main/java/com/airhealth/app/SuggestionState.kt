package com.airhealth.app

enum class SuggestionEntryPoint(
    val wireValue: String,
) {
    FEATURE_HUB("feature_hub"),
    RESULT_CONTEXT("result_context"),
}

data class FeatureSuggestion(
    val feature: FeatureKind,
    val headline: String,
    val body: String,
    val supportingActionLabel: String,
    val entryPoint: SuggestionEntryPoint,
    val cachedAtEpochMillis: Long,
    val refreshRevision: Int,
)

data class SuggestionDraftTemplate(
    val feature: FeatureKind,
    val headline: String,
    val body: String,
    val supportingActionLabel: String,
)

data class SuggestionCacheState(
    private val suggestions: Map<FeatureKind, FeatureSuggestion> = emptyMap(),
) {
    fun suggestionFor(feature: FeatureKind): FeatureSuggestion? = suggestions[feature]

    fun refresh(
        feature: FeatureKind,
        entryPoint: SuggestionEntryPoint,
        templates: List<SuggestionDraftTemplate>,
        cachedAtEpochMillis: Long,
    ): SuggestionCacheState {
        val previous = suggestions[feature]
        val nextRevision = (previous?.refreshRevision ?: 0) + 1
        val template = templates[(nextRevision - 1) % templates.size]
        val nextSuggestion = FeatureSuggestion(
            feature = feature,
            headline = template.headline,
            body = template.body,
            supportingActionLabel = template.supportingActionLabel,
            entryPoint = entryPoint,
            cachedAtEpochMillis = cachedAtEpochMillis,
            refreshRevision = nextRevision,
        )
        return copy(suggestions = suggestions + (feature to nextSuggestion))
    }
}

fun suggestionTemplatesFor(
    feature: FeatureKind,
    goal: FeatureGoal?,
): List<SuggestionDraftTemplate> {
    val goalTail = goal?.targetLabel?.let { " Keep your goal target of $it in view while you work through it." } ?: ""

    return when (feature) {
        FeatureKind.ORAL_HEALTH -> listOf(
            SuggestionDraftTemplate(
                feature = feature,
                headline = "Keep your oral baseline steady",
                body = "Try checking at the same time each morning for the next few sessions so trend shifts are easier to compare.$goalTail",
                supportingActionLabel = "Morning consistency plan",
            ),
            SuggestionDraftTemplate(
                feature = feature,
                headline = "Use the next reading as a recovery checkpoint",
                body = "If sensitivity has been fluctuating, take the next oral check after your usual brushing routine so the reading stays comparable to the last one.$goalTail",
                supportingActionLabel = "Recovery checkpoint",
            ),
        )

        FeatureKind.FAT_BURNING -> listOf(
            SuggestionDraftTemplate(
                feature = feature,
                headline = "Repeat a comparable training-day check",
                body = "Run the next fat-burning session after a workout day with similar intensity so the delta stays meaningful against your recent baseline.$goalTail",
                supportingActionLabel = "Comparable workout session",
            ),
            SuggestionDraftTemplate(
                feature = feature,
                headline = "Use the next session to validate recovery",
                body = "Schedule the next guided session after a full recovery block so you can compare best-delta movement without fatigue noise.$goalTail",
                supportingActionLabel = "Recovery validation session",
            ),
        )
    }
}
