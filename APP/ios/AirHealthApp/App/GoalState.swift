import Foundation

struct FeatureGoal {
    let feature: FeatureKind
    let summary: String
    let targetLabel: String
    let cadenceLabel: String
    let updatedAtEpochMillis: Int64
    let revision: Int
}

struct GoalDraftTemplate: Identifiable {
    let feature: FeatureKind
    let title: String
    let summary: String
    let targetLabel: String
    let cadenceLabel: String

    var id: String {
        "\(feature.rawValue)-\(title)"
    }
}

struct GoalCacheState {
    private let goals: [FeatureKind: FeatureGoal]

    init(goals: [FeatureKind: FeatureGoal] = [:]) {
        self.goals = goals
    }

    func goal(for feature: FeatureKind) -> FeatureGoal? {
        goals[feature]
    }

    func upsert(
        feature: FeatureKind,
        summary: String,
        targetLabel: String,
        cadenceLabel: String,
        updatedAtEpochMillis: Int64
    ) -> GoalCacheState {
        let nextRevision = (goals[feature]?.revision ?? 0) + 1
        var nextGoals = goals
        nextGoals[feature] = FeatureGoal(
            feature: feature,
            summary: summary,
            targetLabel: targetLabel,
            cadenceLabel: cadenceLabel,
            updatedAtEpochMillis: updatedAtEpochMillis,
            revision: nextRevision
        )
        return GoalCacheState(goals: nextGoals)
    }

    func remove(feature: FeatureKind) -> GoalCacheState {
        var nextGoals = goals
        nextGoals.removeValue(forKey: feature)
        return GoalCacheState(goals: nextGoals)
    }
}

func goalTemplates(for feature: FeatureKind) -> [GoalDraftTemplate] {
    switch feature {
    case .oralHealth:
        return [
            GoalDraftTemplate(
                feature: feature,
                title: "Morning consistency goal",
                summary: "Measure oral health every morning before breakfast.",
                targetLabel: "7 morning checks",
                cadenceLabel: "Weekly cadence"
            ),
            GoalDraftTemplate(
                feature: feature,
                title: "Sensitivity reset goal",
                summary: "Stabilize gum sensitivity after dental work or flare-ups.",
                targetLabel: "3 stable readings",
                cadenceLabel: "10-day cadence"
            ),
        ]
    case .fatBurning:
        return [
            GoalDraftTemplate(
                feature: feature,
                title: "Training block goal",
                summary: "Complete guided fat-burning checks through the current training block.",
                targetLabel: "5 guided sessions",
                cadenceLabel: "Weekly cadence"
            ),
            GoalDraftTemplate(
                feature: feature,
                title: "Recovery delta goal",
                summary: "Improve post-session recovery trend after hard training days.",
                targetLabel: "2% best-delta lift",
                cadenceLabel: "14-day cadence"
            ),
        ]
    }
}
