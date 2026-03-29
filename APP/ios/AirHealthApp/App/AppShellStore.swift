import SwiftUI

enum ManagedAction: String {
    case setup = "setup"
    case setGoals = "set_goals"
    case viewHistory = "view_history"
    case measure = "measure"
    case getSuggestion = "get_suggestion"
    case consultProfessionals = "consult_professionals"

    var title: String {
        switch self {
        case .setup:
            return "Setup"
        case .setGoals:
            return "Set Goals"
        case .viewHistory:
            return "View History"
        case .measure:
            return "Measure"
        case .getSuggestion:
            return "Get Suggestion"
        case .consultProfessionals:
            return "Consult Professionals"
        }
    }

    static func from(_ action: FeatureAction) -> ManagedAction {
        switch action {
        case .setGoals:
            return .setGoals
        case .viewHistory:
            return .viewHistory
        case .measure:
            return .measure
        case .getSuggestion:
            return .getSuggestion
        case .consultProfessionals:
            return .consultProfessionals
        }
    }
}

enum ActionLockReasonCode: String {
    case conflictingActionInProgress = "conflicting_action_in_progress"
}

struct BlockedActionAttempt {
    let requestedFeature: FeatureKind
    let requestedAction: ManagedAction
    let activeFeature: FeatureKind
    let activeAction: ManagedAction
    let reasonCode: ActionLockReasonCode

    var message: String {
        "Finish \(activeAction.title) for \(activeFeature.title) before starting \(requestedAction.title)."
    }
}

struct ActionLockState {
    var activeFeature: FeatureKind?
    var activeAction: ManagedAction?
    var blockedAttempt: BlockedActionAttempt?

    mutating func tryAcquire(feature: FeatureKind, action: ManagedAction) -> Bool {
        if let activeAction, let activeFeature {
            if activeAction == action && activeFeature == feature {
                blockedAttempt = nil
                return true
            }

            blockedAttempt = BlockedActionAttempt(
                requestedFeature: feature,
                requestedAction: action,
                activeFeature: activeFeature,
                activeAction: activeAction,
                reasonCode: .conflictingActionInProgress
            )
            return false
        }

        activeFeature = feature
        activeAction = action
        blockedAttempt = nil
        return true
    }

    mutating func release() {
        activeFeature = nil
        activeAction = nil
        blockedAttempt = nil
    }

    mutating func clearBlockedAttempt() {
        blockedAttempt = nil
    }
}

enum AppRoute {
    case home
    case featureHub(SelectedFeatureContext)
    case featureAction(SelectedFeatureContext, FeatureAction)

    var routeID: String {
        switch self {
        case .home:
            return "home"
        case let .featureHub(context):
            return "feature_hub/\(context.feature.rawValue)"
        case let .featureAction(context, action):
            return "feature_hub/\(context.feature.rawValue)/\(action.rawValue)"
        }
    }
}

@MainActor
final class AppShellStore: ObservableObject {
    @Published private(set) var route: AppRoute = .home
    @Published private(set) var actionLockState = ActionLockState()

    var lastBlockedActionAttempt: BlockedActionAttempt? {
        actionLockState.blockedAttempt
    }

    var activeManagedAction: ManagedAction? {
        actionLockState.activeAction
    }

    func openFeature(_ feature: FeatureKind) {
        var nextLockState = actionLockState
        nextLockState.clearBlockedAttempt()
        actionLockState = nextLockState
        route = .featureHub(
            SelectedFeatureContext(
                feature: feature,
                lastVisitedRouteID: "home"
            )
        )
    }

    func openAction(_ action: FeatureAction) {
        guard let context = currentFeatureContext() else {
            return
        }

        var nextLockState = actionLockState
        let wasAcquired = nextLockState.tryAcquire(
            feature: context.feature,
            action: ManagedAction.from(action)
        )
        actionLockState = nextLockState

        guard wasAcquired else {
            return
        }

        route = .featureAction(
            SelectedFeatureContext(
                feature: context.feature,
                lastVisitedRouteID: "feature_hub/\(context.feature.rawValue)"
            ),
            action
        )
    }

    func returnToFeature() {
        guard case let .featureAction(context, _) = route else {
            return
        }

        var nextLockState = actionLockState
        nextLockState.release()
        actionLockState = nextLockState
        route = .featureHub(context)
    }

    func returnHome() {
        var nextLockState = actionLockState
        if case .featureAction = route {
            nextLockState.release()
        } else {
            nextLockState.clearBlockedAttempt()
        }
        actionLockState = nextLockState

        route = .home
    }

    private func currentFeatureContext() -> SelectedFeatureContext? {
        switch route {
        case .home:
            return nil
        case let .featureHub(context):
            return context
        case let .featureAction(context, _):
            return context
        }
    }
}
