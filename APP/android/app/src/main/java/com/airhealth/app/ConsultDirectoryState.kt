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
    val externalUrl: String,
    val launchLabel: String,
    val leaveAirHealthMessage: String,
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

data class PendingConsultHandoff(
    val resource: ConsultDirectoryResource,
    val requestedAtEpochMillis: Long,
)

data class ConsultHandoffEvent(
    val feature: String,
    val resourceTitle: String,
    val targetHost: String,
    val launchLabel: String,
)

class ConsultHandoffAnalytics {
    private val recordedEvents = mutableListOf<ConsultHandoffEvent>()

    val events: List<ConsultHandoffEvent>
        get() = recordedEvents

    fun recordLaunched(resource: ConsultDirectoryResource) {
        recordedEvents += ConsultHandoffEvent(
            feature = resource.feature.routeId,
            resourceTitle = resource.title,
            targetHost = resource.externalUrl.substringAfter("://").substringBefore("/"),
            launchLabel = resource.launchLabel,
        )
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
                externalUrl = "https://care.airhealth.app/oral-wellness-coach",
                launchLabel = "Open virtual consult",
                leaveAirHealthMessage = "This opens the AirHealth care network in your browser. AirHealth keeps your measurement details here unless you choose to share them later.",
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
                externalUrl = "https://providers.airhealth.app/preventive-dentistry",
                launchLabel = "Open clinic site",
                leaveAirHealthMessage = "You are leaving AirHealth for a partner clinic site. No session payload is sent with this handoff.",
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
                externalUrl = "https://care.airhealth.app/metabolic-performance",
                launchLabel = "Open coach profile",
                leaveAirHealthMessage = "This opens the AirHealth care network in your browser. AirHealth keeps your device and session details inside the app.",
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
                externalUrl = "https://partners.airhealth.app/recovery-nutrition",
                launchLabel = "Open advisor site",
                leaveAirHealthMessage = "You are leaving AirHealth for an external advisor site. The handoff only carries the destination link, not your measurement history.",
            ),
        )
    }
}
