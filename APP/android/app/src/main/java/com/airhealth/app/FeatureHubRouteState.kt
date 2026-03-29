package com.airhealth.app

enum class FeatureKind(
    val routeId: String,
    val title: String,
    val subtitle: String,
) {
    ORAL_HEALTH(
        routeId = "oral_health",
        title = "Oral Health",
        subtitle = "Track oral-health trends over time.",
    ),
    FAT_BURNING(
        routeId = "fat_burning",
        title = "Fat Burning",
        subtitle = "Follow repeated breath sessions and best-delta progress.",
    ),
}

enum class FeatureAction(
    val routeId: String,
    val title: String,
) {
    SET_GOALS("set_goals", "Set Goals"),
    VIEW_HISTORY("view_history", "View History"),
    MEASURE("measure", "Measure"),
    GET_SUGGESTION("get_suggestion", "Get Suggestion"),
    CONSULT_PROFESSIONALS("consult_professionals", "Consult Professionals"),
}

data class SelectedFeatureContext(
    val feature: FeatureKind,
    val lastVisitedRouteId: String,
)

sealed class FeatureHubRoute {
    data object Home : FeatureHubRoute()

    data class Feature(val context: SelectedFeatureContext) : FeatureHubRoute()

    data class Action(
        val context: SelectedFeatureContext,
        val action: FeatureAction,
    ) : FeatureHubRoute()

    val routeId: String
        get() = when (this) {
            Home -> "home"
            is Feature -> "feature_hub/${context.feature.routeId}"
            is Action -> "feature_hub/${context.feature.routeId}/${action.routeId}"
        }
}

class FeatureHubRouteState(
    initialRoute: FeatureHubRoute = FeatureHubRoute.Home,
) {
    var route: FeatureHubRoute = initialRoute
        private set

    fun openFeature(feature: FeatureKind) {
        route = FeatureHubRoute.Feature(
            SelectedFeatureContext(
                feature = feature,
                lastVisitedRouteId = FeatureHubRoute.Home.routeId,
            ),
        )
    }

    fun openAction(action: FeatureAction) {
        val currentFeatureRoute = route as? FeatureHubRoute.Feature ?: return
        route = FeatureHubRoute.Action(
            context = currentFeatureRoute.context.copy(
                lastVisitedRouteId = currentFeatureRoute.routeId,
            ),
            action = action,
        )
    }

    fun returnToFeature() {
        val currentActionRoute = route as? FeatureHubRoute.Action ?: return
        route = FeatureHubRoute.Feature(currentActionRoute.context)
    }

    fun returnHome() {
        route = FeatureHubRoute.Home
    }
}
