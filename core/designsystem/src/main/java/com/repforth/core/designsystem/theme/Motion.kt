package com.repforth.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/*
 * Motion, ported from design-system/tokens/motion.css.
 *
 * The CSS carries the rules as a comment and they are the reason these are
 * tokens rather than numbers typed at each call site:
 *
 *   shared-axis for plan -> detail, spring for set completion, continuous
 *   rotation ONLY for an active timer ring, and NO large motion while a set is
 *   in progress.
 *
 * The last one is a product rule, not a taste: someone mid-set is holding a
 * barbell and looking at a number, and a screen that moves under them at that
 * moment is worse than a still one.
 */

/** Durations in milliseconds. `--dur-*` in the CSS. */
object Dur {
    const val instant = 50
    const val quick = 100
    const val short = 150
    const val medium = 250
    const val long = 400
    const val xlong = 550
}

/**
 * Easing curves. `--ease-*` in the CSS.
 *
 * The CSS declares `--ease-emphasized` with the same curve as
 * `--ease-standard`; it is deliberately not repeated here under a second name,
 * because two names for one value is the duplication this repo keeps deleting.
 */
object Ease {
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Overshoots past 1 on purpose — the y2 of 1.28 is the pop. */
    val spring: Easing = CubicBezierEasing(0.18f, 0.89f, 0.32f, 1.28f)

    val linear: Easing = LinearEasing
}

/** How far a thing travels as it enters or leaves. `--motion-travel-*`. */
object Travel {
    val sm = 8.dp
    val md = 24.dp
    val lg = 56.dp
}

/** `--motion-press-scale` and `--motion-pop-scale`. */
object MotionScale {
    const val press = 0.97f
    const val pop = 1.06f
}

/**
 * Whether the user asked for less movement.
 *
 * Provided by [RepForthTheme] rather than carried through a ViewModel, for the
 * same reason `LocalUnitSystem` is: it is a display decision that reaches
 * almost every screen and almost none of them have another reason to know
 * about preferences.
 *
 * `static` because it changes about once in the life of an install, and a
 * static local skips invalidating every reader that merely passes it through.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * A duration that collapses to nothing when the user asked for less movement.
 *
 * The CSS does this with a global clamp — every duration to 1ms and every
 * travel to 0 — which is not available here, so it has to be asked for at the
 * point the spec is built. That is what [rfTween] and [rfTravel] are for; using
 * `tween(Dur.short)` directly is the way to ship an animation the setting
 * cannot switch off, and the guard test in `MotionTokenTest` is what catches it.
 */
@Composable
@ReadOnlyComposable
fun rfDuration(millis: Int): Int = reducedDuration(millis, LocalReducedMotion.current)

/**
 * The decision itself, without a composition around it.
 *
 * Split out so it can be tested by a plain JUnit test, the same way
 * `toStepValue` is: asserting it through a composition would mean adding
 * Robolectric to this module for one comparison.
 */
internal fun reducedDuration(millis: Int, reduced: Boolean): Int = if (reduced) 0 else millis

/** The tween to reach for. Honours reduced motion; [tween] does not. */
@Composable
@ReadOnlyComposable
fun <T> rfTween(
    durationMillis: Int = Dur.medium,
    easing: Easing = Ease.standard,
): FiniteAnimationSpec<T> = tween(durationMillis = rfDuration(durationMillis), easing = easing)

/** Travel distance, zeroed when the user asked for less movement. */
@Composable
@ReadOnlyComposable
fun rfTravel(distance: androidx.compose.ui.unit.Dp) =
    if (LocalReducedMotion.current) 0.dp else distance

/**
 * A scale that pops once, each time [key] changes.
 *
 * The design system's second motion rule is "spring for set completion". This
 * is that spring, expressed as a value rather than a component so the thing
 * that pops can be any composable — today the set counter, tomorrow a weight
 * that went up.
 *
 * **It does not fire on the first composition.** A pop is a reaction to a
 * change, and a screen that pops everything the moment it opens is announcing
 * nothing. Getting this wrong is invisible in a screenshot and obvious on a
 * device, so the first value of [key] is recorded and skipped.
 *
 * Reduced motion returns a flat 1f and never starts the animation, for the same
 * reason `CoachScreen` branches: this is a sequence of two tweens, and
 * collapsing their durations to zero would still run the sequence.
 *
 * The overshoot is [MotionScale.pop] — 1.06, from `--motion-pop-scale`. On the
 * running-workout screen it is deliberately the largest motion allowed while a
 * set is in progress, which is to say: barely any.
 */
@Composable
fun rfPopOnChange(key: Any?): Float {
    val reduced = LocalReducedMotion.current
    if (reduced) return 1f

    val scale = remember { Animatable(1f) }
    var seen by remember { mutableStateOf<Any?>(key) }

    LaunchedEffect(key) {
        if (key == seen) return@LaunchedEffect
        seen = key
        scale.animateTo(MotionScale.pop, tween(durationMillis = Dur.quick, easing = Ease.decelerate))
        scale.animateTo(1f, tween(durationMillis = Dur.short, easing = Ease.spring))
    }
    return scale.value
}
