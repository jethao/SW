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

enum ActionGateOutcome: String {
    case allowed = "allowed"
    case blocked = "blocked"
}

struct ActionGateEvent {
    let feature: String
    let requestedAction: String
    let outcome: String
    let activeAction: String?
    let reasonCode: String?

    var payload: [String: String] {
        var payload = [
            "feature": feature,
            "requested_action": requestedAction,
            "outcome": outcome,
        ]

        if let activeAction {
            payload["active_action"] = activeAction
        }

        if let reasonCode {
            payload["reason_code"] = reasonCode
        }

        return payload
    }
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
    @Published private(set) var actionGateEvents: [ActionGateEvent] = []

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

        let requestedAction = ManagedAction.from(action)
        var nextLockState = actionLockState
        let wasAcquired = nextLockState.tryAcquire(
            feature: context.feature,
            action: requestedAction
        )
        actionLockState = nextLockState

        guard wasAcquired else {
            if let blockedAttempt = nextLockState.blockedAttempt {
                actionGateEvents.append(
                    ActionGateEvent(
                        feature: blockedAttempt.requestedFeature.rawValue,
                        requestedAction: blockedAttempt.requestedAction.rawValue,
                        outcome: ActionGateOutcome.blocked.rawValue,
                        activeAction: blockedAttempt.activeAction.rawValue,
                        reasonCode: blockedAttempt.reasonCode.rawValue
                    )
                )
            }
            return
        }

        actionGateEvents.append(
            ActionGateEvent(
                feature: context.feature.rawValue,
                requestedAction: requestedAction.rawValue,
                outcome: ActionGateOutcome.allowed.rawValue,
                activeAction: nil,
                reasonCode: nil
            )
        )

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
