import Foundation

enum SuggestionEntryPoint: String {
    case featureHub = "feature_hub"
    case resultContext = "result_context"
}

struct FeatureSuggestion {
    let feature: FeatureKind
    let headline: String
    let body: String
    let supportingActionLabel: String
    let entryPoint: SuggestionEntryPoint
    let cachedAtEpochMillis: Int64
    let refreshRevision: Int
}

struct SuggestionDraftTemplate: Identifiable {
    let feature: FeatureKind
    let headline: String
    let body: String
    let supportingActionLabel: String

    var id: String {
        "\(feature.rawValue)-\(headline)"
    }
}

struct SuggestionCacheState {
    private let suggestions: [FeatureKind: FeatureSuggestion]

    init(suggestions: [FeatureKind: FeatureSuggestion] = [:]) {
        self.suggestions = suggestions
    }

    func suggestion(for feature: FeatureKind) -> FeatureSuggestion? {
        suggestions[feature]
    }

    func refresh(
        feature: FeatureKind,
        entryPoint: SuggestionEntryPoint,
        templates: [SuggestionDraftTemplate],
        cachedAtEpochMillis: Int64
    ) -> SuggestionCacheState {
        let previous = suggestions[feature]
        let nextRevision = (previous?.refreshRevision ?? 0) + 1
        let template = templates[(nextRevision - 1) % templates.count]

        var nextSuggestions = suggestions
        nextSuggestions[feature] = FeatureSuggestion(
            feature: feature,
            headline: template.headline,
            body: template.body,
            supportingActionLabel: template.supportingActionLabel,
            entryPoint: entryPoint,
            cachedAtEpochMillis: cachedAtEpochMillis,
            refreshRevision: nextRevision
        )
        return SuggestionCacheState(suggestions: nextSuggestions)
    }
}

func suggestionTemplates(
    for feature: FeatureKind,
    goal: FeatureGoal?
) -> [SuggestionDraftTemplate] {
    let goalTail = goal.map { " Keep your goal target of \($0.targetLabel) in view while you work through it." } ?? ""

    switch feature {
    case .oralHealth:
        return [
            SuggestionDraftTemplate(
                feature: feature,
                headline: "Keep your oral baseline steady",
                body: "Try checking at the same time each morning for the next few sessions so trend shifts are easier to compare.\(goalTail)",
                supportingActionLabel: "Morning consistency plan"
            ),
            SuggestionDraftTemplate(
                feature: feature,
                headline: "Use the next reading as a recovery checkpoint",
                body: "If sensitivity has been fluctuating, take the next oral check after your usual brushing routine so the reading stays comparable to the last one.\(goalTail)",
                supportingActionLabel: "Recovery checkpoint"
            ),
        ]
    case .fatBurning:
        return [
            SuggestionDraftTemplate(
                feature: feature,
                headline: "Repeat a comparable training-day check",
                body: "Run the next fat-burning session after a workout day with similar intensity so the delta stays meaningful against your recent baseline.\(goalTail)",
                supportingActionLabel: "Comparable workout session"
            ),
            SuggestionDraftTemplate(
                feature: feature,
                headline: "Use the next session to validate recovery",
                body: "Schedule the next guided session after a full recovery block so you can compare best-delta movement without fatigue noise.\(goalTail)",
                supportingActionLabel: "Recovery validation session"
            ),
        ]
    }
}
