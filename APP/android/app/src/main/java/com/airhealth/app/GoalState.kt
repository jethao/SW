package com.airhealth.app

data class FeatureGoal(
    val feature: FeatureKind,
    val summary: String,
    val targetLabel: String,
    val cadenceLabel: String,
    val updatedAtEpochMillis: Long,
    val revision: Int,
)

data class GoalDraftTemplate(
    val feature: FeatureKind,
    val title: String,
    val summary: String,
    val targetLabel: String,
    val cadenceLabel: String,
)

data class GoalCacheState(
    private val goals: Map<FeatureKind, FeatureGoal> = emptyMap(),
) {
    fun goalFor(feature: FeatureKind): FeatureGoal? = goals[feature]

    fun upsert(
        feature: FeatureKind,
        summary: String,
        targetLabel: String,
        cadenceLabel: String,
        updatedAtEpochMillis: Long,
    ): GoalCacheState {
        val nextRevision = (goals[feature]?.revision ?: 0) + 1
        val nextGoal = FeatureGoal(
            feature = feature,
            summary = summary,
            targetLabel = targetLabel,
            cadenceLabel = cadenceLabel,
            updatedAtEpochMillis = updatedAtEpochMillis,
            revision = nextRevision,
        )
        return copy(goals = goals + (feature to nextGoal))
    }

    fun remove(feature: FeatureKind): GoalCacheState {
        return copy(goals = goals - feature)
    }
}

fun goalTemplatesFor(feature: FeatureKind): List<GoalDraftTemplate> {
    return when (feature) {
        FeatureKind.ORAL_HEALTH -> listOf(
            GoalDraftTemplate(
                feature = feature,
                title = "Morning consistency goal",
                summary = "Measure oral health every morning before breakfast.",
                targetLabel = "7 morning checks",
                cadenceLabel = "Weekly cadence",
            ),
            GoalDraftTemplate(
                feature = feature,
                title = "Sensitivity reset goal",
                summary = "Stabilize gum sensitivity after dental work or flare-ups.",
                targetLabel = "3 stable readings",
                cadenceLabel = "10-day cadence",
            ),
        )

        FeatureKind.FAT_BURNING -> listOf(
            GoalDraftTemplate(
                feature = feature,
                title = "Training block goal",
                summary = "Complete guided fat-burning checks through the current training block.",
                targetLabel = "5 guided sessions",
                cadenceLabel = "Weekly cadence",
            ),
            GoalDraftTemplate(
                feature = feature,
                title = "Recovery delta goal",
                summary = "Improve post-session recovery trend after hard training days.",
                targetLabel = "2% best-delta lift",
                cadenceLabel = "14-day cadence",
            ),
        )
    }
}
