import Foundation

struct ConsultDirectoryResource: Identifiable {
    let feature: FeatureKind
    let localeTag: String
    let title: String
    let specialtyLabel: String
    let regionLabel: String
    let availabilityLabel: String
    let detail: String
    let handoffHint: String

    var id: String {
        "\(feature.rawValue)-\(title)"
    }
}

struct FeatureConsultDirectory {
    let feature: FeatureKind
    let localeTag: String
    let cachedAtEpochMillis: Int64
    let refreshRevision: Int
    let resources: [ConsultDirectoryResource]
}

struct ConsultDirectoryCacheState {
    private let directories: [FeatureKind: FeatureConsultDirectory]

    init(directories: [FeatureKind: FeatureConsultDirectory] = [:]) {
        self.directories = directories
    }

    func directory(for feature: FeatureKind) -> FeatureConsultDirectory? {
        directories[feature]
    }

    func refresh(
        feature: FeatureKind,
        localeTag: String,
        cachedAtEpochMillis: Int64
    ) -> ConsultDirectoryCacheState {
        let previous = directories[feature]
        let nextRevision = (previous?.refreshRevision ?? 0) + 1
        var nextDirectories = directories
        nextDirectories[feature] = FeatureConsultDirectory(
            feature: feature,
            localeTag: localeTag,
            cachedAtEpochMillis: cachedAtEpochMillis,
            refreshRevision: nextRevision,
            resources: consultDirectoryTemplates(for: feature, localeTag: localeTag)
        )
        return ConsultDirectoryCacheState(directories: nextDirectories)
    }
}

func consultDirectoryTemplates(
    for feature: FeatureKind,
    localeTag: String
) -> [ConsultDirectoryResource] {
    switch feature {
    case .oralHealth:
        return [
            ConsultDirectoryResource(
                feature: feature,
                localeTag: localeTag,
                title: "AirHealth Oral Wellness Coach",
                specialtyLabel: "Dental hygienist support",
                regionLabel: "Local virtual care network",
                availabilityLabel: "Weekday virtual consults",
                detail: "General oral-health coaching and trend interpretation support for recurring gum-sensitivity or hygiene questions.",
                handoffHint: "Directory-only support listing. External handoff stays in the next consult ticket."
            ),
            ConsultDirectoryResource(
                feature: feature,
                localeTag: localeTag,
                title: "Community Preventive Dentistry Clinic",
                specialtyLabel: "Preventive dentistry",
                regionLabel: "Regional clinic partner",
                availabilityLabel: "New-patient screening",
                detail: "Consumer-facing preventive follow-up option for baseline changes that may need an in-person dental screening.",
                handoffHint: "Keep session details inside AirHealth until the user explicitly leaves the app."
            ),
        ]
    case .fatBurning:
        return [
            ConsultDirectoryResource(
                feature: feature,
                localeTag: localeTag,
                title: "Metabolic Performance Coach",
                specialtyLabel: "Exercise physiology support",
                regionLabel: "Local virtual care network",
                availabilityLabel: "Evening video sessions",
                detail: "Consumer-safe coaching resource for interpreting repeated fat-burning trend changes across training blocks.",
                handoffHint: "Directory-only support listing. External handoff stays in the next consult ticket."
            ),
            ConsultDirectoryResource(
                feature: feature,
                localeTag: localeTag,
                title: "Recovery Nutrition Advisor",
                specialtyLabel: "Sports nutrition guidance",
                regionLabel: "Regional wellness partner",
                availabilityLabel: "Next-week availability",
                detail: "Nutrition-focused support option for users tracking recovery or training-day pattern changes.",
                handoffHint: "No measurement payload leaves AirHealth in this ticket."
            ),
        ]
    }
}
