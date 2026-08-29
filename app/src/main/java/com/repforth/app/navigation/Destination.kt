package com.repforth.app.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.repforth.app.R
import com.repforth.core.designsystem.component.RfIcons
import kotlinx.serialization.Serializable

/**
 * Every destination in the app graph.
 *
 * These are `@Serializable` types rather than route strings so the compiler
 * checks navigation: a typo in a route, or a missing argument once detail
 * screens gain one, stops being a runtime crash on a device nobody is watching.
 */
sealed interface Destination {

    @Serializable data object Today : Destination

    @Serializable data object Plans : Destination

    @Serializable data object Exercises : Destination

    @Serializable data object Progress : Destination

    /**
     * Reached from the top bar, not the bottom bar (§12). It is in the same
     * graph precisely so that opening it and pressing back returns to whichever
     * tab was showing, without any of that being written by hand.
     */
    @Serializable data object Settings : Destination

    /**
     * The workout builder (§12): not a tab, opened from Plans and Today, and
     * from the edit action on a saved plan.
     *
     * [planId] carries which plan is being edited, or null for a new one. It is
     * an argument rather than two destinations because the screen is the same
     * either way — §12 requires a generated or saved plan to be editable in the
     * same cards a new one is built from.
     */
    @Serializable data class Builder(val planId: String? = null) : Destination
}

/**
 * The four bottom-bar destinations, in order (§12).
 *
 * The builder is deliberately absent: it opens from Today and Plans, and is not
 * a top-level destination. So is Coach, which is a mode inside the builder
 * rather than a fifth tab — see the guideline for why that is not a UI detail.
 */
enum class TopLevelDestination(
    val route: Destination,
    @param:StringRes val labelRes: Int,
) {
    TODAY(Destination.Today, R.string.today),
    PLANS(Destination.Plans, R.string.plans),
    EXERCISES(Destination.Exercises, R.string.catalog),
    PROGRESS(Destination.Progress, R.string.progress),
    ;

    /**
     * Resolved through [RfIcons] rather than stored, so the icon set can be
     * swapped without touching the navigation model.
     */
    val icon: Painter
        @Composable get() = when (this) {
            TODAY -> RfIcons.Today
            PLANS -> RfIcons.Plans
            EXERCISES -> RfIcons.Exercises
            PROGRESS -> RfIcons.Progress
        }
}
