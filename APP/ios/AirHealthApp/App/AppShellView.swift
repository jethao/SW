import SwiftUI

enum FeatureKind: String, CaseIterable, Identifiable {
    case oralHealth = "oral_health"
    case fatBurning = "fat_burning"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .oralHealth:
            return "Oral Health"
        case .fatBurning:
            return "Fat Burning"
        }
    }

    var subtitle: String {
        switch self {
        case .oralHealth:
            return "Track oral-health trends over time."
        case .fatBurning:
            return "Follow repeated breath sessions and best-delta progress."
        }
    }
}

enum FeatureAction: String, CaseIterable, Identifiable {
    case setGoals = "set_goals"
    case viewHistory = "view_history"
    case measure = "measure"
    case getSuggestion = "get_suggestion"
    case consultProfessionals = "consult_professionals"

    var id: String { rawValue }

    var title: String {
        switch self {
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
}

struct SelectedFeatureContext {
    let feature: FeatureKind
    let lastVisitedRouteID: String
}

private enum AppRoute {
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

struct AppShellView: View {
    @State private var route: AppRoute = .home

    var body: some View {
        NavigationStack {
            content
                .navigationTitle(title)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch route {
        case .home:
            FeatureHubHomeView(
                onSelectFeature: { feature in
                    route = .featureHub(
                        SelectedFeatureContext(
                            feature: feature,
                            lastVisitedRouteID: "home"
                        )
                    )
                }
            )
        case let .featureHub(context):
            FeatureActionsView(
                context: context,
                onSelectAction: { action in
                    route = .featureAction(
                        SelectedFeatureContext(
                            feature: context.feature,
                            lastVisitedRouteID: "feature_hub/\(context.feature.rawValue)"
                        ),
                        action
                    )
                },
                onReturnHome: {
                    route = .home
                }
            )
        case let .featureAction(context, action):
            FeatureActionDestinationView(
                context: context,
                action: action,
                onReturnToFeature: {
                    route = .featureHub(context)
                },
                onReturnHome: {
                    route = .home
                }
            )
        }
    }

    private var title: String {
        switch route {
        case .home:
            return "AirHealth"
        case let .featureHub(context):
            return context.feature.title
        case let .featureAction(_, action):
            return action.title
        }
    }
}

private struct FeatureHubHomeView: View {
    let onSelectFeature: (FeatureKind) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Choose a feature")
                    .font(.title2.bold())

                Text("Start from the home feature hub, keep the selected feature context, and route to the next action from there.")
                    .foregroundStyle(.secondary)

                ForEach(FeatureKind.allCases) { feature in
                    Button(action: { onSelectFeature(feature) }) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(feature.title)
                                .font(.headline)
                            Text(feature.subtitle)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text("Enter \(feature.title) flow")
                                .font(.footnote.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding()
        }
    }
}

private struct FeatureActionsView: View {
    let context: SelectedFeatureContext
    let onSelectAction: (FeatureAction) -> Void
    let onReturnHome: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Selected feature context")
                .font(.headline)

            Text("\(context.feature.title) is active. Every child route inherits this feature context and can return here without losing it.")
                .foregroundStyle(.secondary)

            Text("Current route ID: feature_hub/\(context.feature.rawValue)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            ForEach(FeatureAction.allCases) { action in
                Button(action.title) {
                    onSelectAction(action)
                }
                .buttonStyle(.borderedProminent)
            }

            Button("Back To Home") {
                onReturnHome()
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding()
    }
}

private struct FeatureActionDestinationView: View {
    let context: SelectedFeatureContext
    let action: FeatureAction
    let onReturnToFeature: () -> Void
    let onReturnHome: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(action.title)
                .font(.title3.bold())

            Text("This child route inherits the \(context.feature.title) context and preserves return-to-feature behavior.")
                .foregroundStyle(.secondary)

            Text("Selected feature: \(context.feature.title)")
            Text("Return route ID: \(context.lastVisitedRouteID)")
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            Button("Return To \(context.feature.title)") {
                onReturnToFeature()
            }
            .buttonStyle(.borderedProminent)

            Button("Return To Home") {
                onReturnHome()
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding()
    }
}
