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
    let externalURL: URL
    let launchLabel: String
    let leaveAirHealthMessage: String

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

struct PendingConsultHandoff {
    let resource: ConsultDirectoryResource
    let requestedAtEpochMillis: Int64
}

struct ConsultHandoffEvent {
    let feature: String
    let resourceTitle: String
    let targetHost: String
    let launchLabel: String
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
                handoffHint: "Directory-only support listing. External handoff stays in the next consult ticket.",
                externalURL: URL(string: "https://care.airhealth.app/oral-wellness-coach")!,
                launchLabel: "Open virtual consult",
                leaveAirHealthMessage: "This opens the AirHealth care network in your browser. AirHealth keeps your measurement details here unless you choose to share them later."
            ),
            ConsultDirectoryResource(
                feature: feature,
                localeTag: localeTag,
                title: "Community Preventive Dentistry Clinic",
                specialtyLabel: "Preventive dentistry",
                regionLabel: "Regional clinic partner",
                availabilityLabel: "New-patient screening",
                detail: "Consumer-facing preventive follow-up option for baseline changes that may need an in-person dental screening.",
                handoffHint: "Keep session details inside AirHealth until the user explicitly leaves the app.",
                externalURL: URL(string: "https://providers.airhealth.app/preventive-dentistry")!,
                launchLabel: "Open clinic site",
                leaveAirHealthMessage: "You are leaving AirHealth for a partner clinic site. No session payload is sent with this handoff."
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
                handoffHint: "Directory-only support listing. External handoff stays in the next consult ticket.",
                externalURL: URL(string: "https://care.airhealth.app/metabolic-performance")!,
                launchLabel: "Open coach profile",
                leaveAirHealthMessage: "This opens the AirHealth care network in your browser. AirHealth keeps your device and session details inside the app."
            ),
            ConsultDirectoryResource(
                feature: feature,
                localeTag: localeTag,
                title: "Recovery Nutrition Advisor",
                specialtyLabel: "Sports nutrition guidance",
                regionLabel: "Regional wellness partner",
                availabilityLabel: "Next-week availability",
                detail: "Nutrition-focused support option for users tracking recovery or training-day pattern changes.",
                handoffHint: "No measurement payload leaves AirHealth in this ticket.",
                externalURL: URL(string: "https://partners.airhealth.app/recovery-nutrition")!,
                launchLabel: "Open advisor site",
                leaveAirHealthMessage: "You are leaving AirHealth for an external advisor site. The handoff only carries the destination link, not your measurement history."
            ),
        ]
    }
}
