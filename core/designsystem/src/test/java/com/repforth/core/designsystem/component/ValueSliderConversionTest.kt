package com.repforth.core.designsystem.component

import com.repforth.core.model.WorkoutLimits
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every slider stop maps back to the whole number it stands for.
 *
 * Compose builds a snapped stop by interpolating between the ends, so what
 * arrives in `onValueChange` is a float that is only approximately the stop.
 * Rounding rather than truncating is what makes that safe, and this walks every
 * stop of both sliders the way Compose computes them.
 *
 * Worth being precise about what this does and does not prove. Day six of seven
 * could not be selected on a device, and truncation was the cause: rebuilt with
 * `toInt()` and swept with `adb input tap` across the track, the reachable
 * values were 1, 2, 3, 4, 5, 7 -- the whole band that should read six read five.
 *
 * These first two tests do not reproduce that. They model the stop positions as
 * `start + fraction * (end - start)` in float32, where every stop of both
 * sliders lands exactly and `toInt()` passes. Compose's real arithmetic does
 * not agree with that model, and the disagreement is the entire bug -- so the
 * model is the thing that was wrong, not the theory it was testing.
 *
 * The third test uses a range where even this conservative model produces
 * inexact stops, so the guard is known to be able to go red. The bug itself is
 * only catchable on a device, which is the argument for instrumentation tests
 * recorded in docs/PLAN.md.
 *
 * It travelled here from `feature:onboarding` with the slider itself, when
 * Settings and Coach both needed one. A guard that stays behind while the code
 * it guards moves is worse than no guard, because it goes on passing.
 */
class ValueSliderConversionTest {

    private fun stopsOf(range: IntRange, step: Int): List<Float> {
        val gaps = (range.last - range.first) / step
        return (0..gaps).map { index ->
            val fraction = index.toFloat() / gaps
            range.first + fraction * (range.last - range.first)
        }
    }

    @Test
    fun `every day of the week maps back to itself`() {
        val range = WorkoutLimits.days

        stopsOf(range, step = 1).forEachIndexed { index, raw ->
            assertEquals(
                "Slider stop $raw should be day ${range.first + index}",
                range.first + index,
                raw.toStepValue(range, step = 1),
            )
        }
    }

    @Test
    fun `every session length maps back to itself`() {
        val range = WorkoutLimits.sessionMinutes
        val step = WorkoutLimits.sessionMinutesStep

        stopsOf(range, step).forEachIndexed { index, raw ->
            assertEquals(
                "Slider stop $raw should be ${range.first + index * step} minutes",
                range.first + index * step,
                raw.toStepValue(range, step),
            )
        }
    }

    /**
     * A range whose stops genuinely do not land on exact floats.
     *
     * This is the case the conversion exists for, and unlike the two above it
     * does fail with `toInt()` -- so the guard is known to be able to go red.
     */
    @Test
    fun `a range that does not divide evenly still maps back exactly`() {
        val range = 0..49

        stopsOf(range, step = 1).forEachIndexed { index, raw ->
            assertEquals(index, raw.toStepValue(range, step = 1))
        }
    }

    /**
     * A stepped slider may only return legal stops.
     *
     * The version this replaced rounded the raw value straight to an integer,
     * which is right for a step of one and wrong for any other: a session slider
     * asked for 47.5 answered "48 minutes", which is not one of its stops.
     * Compose snaps before calling back, so it never came up -- but the helper
     * was only correct because of its caller.
     */
    @Test
    fun `a value between stops snaps to a legal one`() {
        val range = WorkoutLimits.sessionMinutes
        val step = WorkoutLimits.sessionMinutesStep

        listOf(47.5f, 46f, 44f, 15.4f, 119.9f).forEach { raw ->
            val snapped = raw.toStepValue(range, step)
            assertEquals(
                "$raw snapped to $snapped, which is not a multiple of $step from ${range.first}",
                0,
                (snapped - range.first) % step,
            )
            assertEquals(true, snapped in range)
        }
    }
}
