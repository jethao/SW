package com.airhealth.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private val routeState = FeatureHubRouteState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderRoute()
    }

    private fun renderRoute() {
        val container = ScrollView(this).apply {
            addView(buildContent())
        }

        setContentView(container)
    }

    private fun buildContent(): View {
        return when (val route = routeState.route) {
            FeatureHubRoute.Home -> buildHomeView()
            is FeatureHubRoute.Feature -> buildFeatureView(route.context)
            is FeatureHubRoute.Action -> buildActionView(route.context, route.action)
        }
    }

    private fun buildHomeView(): View {
        title = "AirHealth"

        return verticalLayout().apply {
            addView(
                headline("Choose a feature")
            )
            addView(
                bodyCopy(
                    "Start from the home feature hub, keep the selected feature context, and route to the next action from there."
                )
            )

            FeatureKind.entries.forEach { feature ->
                addView(
                    actionButton("Enter ${feature.title} flow") {
                        routeState.openFeature(feature)
                        renderRoute()
                    }
                )
                addView(bodyCopy(feature.subtitle))
            }
        }
    }

    private fun buildFeatureView(context: SelectedFeatureContext): View {
        title = context.feature.title

        return verticalLayout().apply {
            addView(headline("Selected feature context"))
            addView(
                bodyCopy(
                    "${context.feature.title} is active. Every child route inherits this feature context and can return here without losing it."
                )
            )
            addView(
                caption("Current route ID: feature_hub/${context.feature.routeId}")
            )

            FeatureAction.entries.forEach { action ->
                addView(
                    actionButton(action.title) {
                        routeState.openAction(action)
                        renderRoute()
                    }
                )
            }

            addView(
                secondaryButton("Back To Home") {
                    routeState.returnHome()
                    renderRoute()
                }
            )
        }
    }

    private fun buildActionView(
        context: SelectedFeatureContext,
        action: FeatureAction,
    ): View {
        title = action.title

        return verticalLayout().apply {
            addView(headline(action.title))
            addView(
                bodyCopy(
                    "This child route inherits the ${context.feature.title} context and preserves return-to-feature behavior."
                )
            )
            addView(bodyCopy("Selected feature: ${context.feature.title}"))
            addView(caption("Return route ID: ${context.lastVisitedRouteId}"))

            addView(
                actionButton("Return To ${context.feature.title}") {
                    routeState.returnToFeature()
                    renderRoute()
                }
            )
            addView(
                secondaryButton("Return To Home") {
                    routeState.returnHome()
                    renderRoute()
                }
            )
        }
    }

    private fun verticalLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
    }

    private fun headline(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 26f
            setPadding(0, 0, 0, 24)
        }
    }

    private fun bodyCopy(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }
    }

    private fun caption(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }
    }

    private fun actionButton(
        text: String,
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
    }

    private fun secondaryButton(
        text: String,
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            this.text = text
            alpha = 0.88f
            setOnClickListener { onClick() }
        }
    }
}
