package com.airhealth.app

data class ConsultDirectoryResource(
    val feature: FeatureKind,
    val localeTag: String,
    val title: String,
    val specialtyLabel: String,
    val regionLabel: String,
    val availabilityLabel: String,
    val detail: String,
    val handoffHint: String,
)

data class FeatureConsultDirectory(
    val feature: FeatureKind,
    val localeTag: String,
    val cachedAtEpochMillis: Long,
    val refreshRevision: Int,
    val resources: List<ConsultDirectoryResource>,
)

data class ConsultDirectoryCacheState(
    private val directories: Map<FeatureKind, FeatureConsultDirectory> = emptyMap(),
) {
    fun directoryFor(feature: FeatureKind): FeatureConsultDirectory? = directories[feature]

    fun refresh(
        feature: FeatureKind,
        localeTag: String,
        cachedAtEpochMillis: Long,
    ): ConsultDirectoryCacheState {
        val previous = directories[feature]
        val nextRevision = (previous?.refreshRevision ?: 0) + 1
        val nextDirectory = FeatureConsultDirectory(
            feature = feature,
            localeTag = localeTag,
            cachedAtEpochMillis = cachedAtEpochMillis,
            refreshRevision = nextRevision,
            resources = consultDirectoryTemplatesFor(feature = feature, localeTag = localeTag),
        )
        return copy(directories = directories + (feature to nextDirectory))
    }
}

fun consultDirectoryTemplatesFor(
    feature: FeatureKind,
    localeTag: String,
): List<ConsultDirectoryResource> {
    return when (feature) {
        FeatureKind.ORAL_HEALTH -> listOf(
            ConsultDirectoryResource(
                feature = feature,
                localeTag = localeTag,
                title = "AirHealth Oral Wellness Coach",
                specialtyLabel = "Dental hygienist support",
                regionLabel = "Local virtual care network",
                availabilityLabel = "Weekday virtual consults",
                detail = "General oral-health coaching and trend interpretation support for recurring gum-sensitivity or hygiene questions.",
                handoffHint = "Directory-only support listing. External handoff stays in the next consult ticket.",
            ),
            ConsultDirectoryResource(
                feature = feature,
                localeTag = localeTag,
                title = "Community Preventive Dentistry Clinic",
                specialtyLabel = "Preventive dentistry",
                regionLabel = "Regional clinic partner",
                availabilityLabel = "New-patient screening",
                detail = "Consumer-facing preventive follow-up option for baseline changes that may need an in-person dental screening.",
                handoffHint = "Keep session details inside AirHealth until the user explicitly leaves the app.",
            ),
        )

        FeatureKind.FAT_BURNING -> listOf(
            ConsultDirectoryResource(
                feature = feature,
                localeTag = localeTag,
                title = "Metabolic Performance Coach",
                specialtyLabel = "Exercise physiology support",
                regionLabel = "Local virtual care network",
                availabilityLabel = "Evening video sessions",
                detail = "Consumer-safe coaching resource for interpreting repeated fat-burning trend changes across training blocks.",
                handoffHint = "Directory-only support listing. External handoff stays in the next consult ticket.",
            ),
            ConsultDirectoryResource(
                feature = feature,
                localeTag = localeTag,
                title = "Recovery Nutrition Advisor",
                specialtyLabel = "Sports nutrition guidance",
                regionLabel = "Regional wellness partner",
                availabilityLabel = "Next-week availability",
                detail = "Nutrition-focused support option for users tracking recovery or training-day pattern changes.",
                handoffHint = "No measurement payload leaves AirHealth in this ticket.",
            ),
        )
    }
}
