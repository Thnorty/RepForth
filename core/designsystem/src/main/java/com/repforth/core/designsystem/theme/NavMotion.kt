package com.repforth.core.designsystem.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/*
 * How one screen replaces another.
 *
 * Two kinds of move, and the design system is explicit that they are not the
 * same thing:
 *
 *   - **Push** — a tab opening a detail screen, Plans to the builder. This has
 *     a direction: the detail came from somewhere and back returns there. It
 *     gets shared-axis X, and the reverse on the way back, which is what makes
 *     "back" feel like undoing rather than like another forward step.
 *   - **Peer** — switching bottom-nav tabs. Progress is not to the right of
 *     Plans in any sense a user could point at, so sliding one in from the
 *     right invents a spatial relationship that does not exist. These fade
 *     through instead.
 *
 * The numbers are the `--motion-enter` and `--motion-exit` recipes from
 * `design-system/tokens/motion.css`: entering is slower and decelerates,
 * leaving is quicker and accelerates. Asymmetric on purpose — the screen being
 * arrived at is the one worth watching.
 *
 * Every one of these goes through [rfTween] and [rfTravel], so reduced motion
 * collapses the duration to nothing and the travel to zero and the result is a
 * cut. That is the whole reason they live here rather than in the nav graph.
 */

/** How far a screen slides. `--motion-travel-md`, in pixels for the slide APIs. */
@Composable
private fun axisTravelPx(): Int {
    val travel = rfTravel(Travel.md)
    return with(LocalDensity.current) { travel.roundToPx() }
}

/** Push: the arriving screen comes from the right. */
@Composable
fun rfPushEnter(): EnterTransition {
    val travel = axisTravelPx()
    return slideInHorizontally(rfTween(Dur.medium, Ease.decelerate)) { travel } +
        fadeIn(rfTween(Dur.medium, Ease.decelerate))
}

/** Push: the screen left behind moves the same way, less far, and goes. */
@Composable
fun rfPushExit(): ExitTransition {
    val travel = axisTravelPx()
    return slideOutHorizontally(rfTween(Dur.short, Ease.accelerate)) { -travel } +
        fadeOut(rfTween(Dur.short, Ease.accelerate))
}

/** Pop: the same axis, reversed, so back undoes the push rather than repeating it. */
@Composable
fun rfPopEnter(): EnterTransition {
    val travel = axisTravelPx()
    return slideInHorizontally(rfTween(Dur.medium, Ease.decelerate)) { -travel } +
        fadeIn(rfTween(Dur.medium, Ease.decelerate))
}

@Composable
fun rfPopExit(): ExitTransition {
    val travel = axisTravelPx()
    return slideOutHorizontally(rfTween(Dur.short, Ease.accelerate)) { travel } +
        fadeOut(rfTween(Dur.short, Ease.accelerate))
}

/** Peer: no direction, so none is implied. */
@Composable
fun rfFadeThroughEnter(): EnterTransition = fadeIn(rfTween(Dur.medium, Ease.decelerate))

@Composable
fun rfFadeThroughExit(): ExitTransition = fadeOut(rfTween(Dur.short, Ease.accelerate))
