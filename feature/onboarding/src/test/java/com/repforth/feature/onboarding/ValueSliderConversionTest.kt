package com.repforth.feature.onboarding

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
 * values were 1, 2, 3, 4, 5, 7 — the whole band that should read six read five.
 *
 * These first two tests do not reproduce that. They model the stop positions as
 * `start + fraction * (end - start)` in float32, where every stop of both
 * sliders lands exactly and `toInt()` passes. Compose's real arithmetic does
 * not agree with that model, and the disagreement is the entire bug — so the
 * model is the thing that was wrong, not the theory it was testing.
 *
 * The third test uses a range where even this conservative model produces
 * inexact stops, so the guard is known to be able to go red. The bug itself is
 * only catchable on a device, which is the argument for instrumentation tests
 * recorded in docs/PLAN.md.
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
        val range = OnboardingUiState.DAYS_RANGE

        stopsOf(range, step = 1).forEachIndexed { index, raw ->
            assertEquals(
                "Slider stop $raw should be day ${range.first + index}",
                range.first + index,
                raw.toStepValue(),
            )
        }
    }

    @Test
    fun `every session length maps back to itself`() {
        val range = OnboardingUiState.SESSION_MINUTES_RANGE
        val step = 5

        stopsOf(range, step).forEachIndexed { index, raw ->
            assertEquals(
                "Slider stop $raw should be ${range.first + index * step} minutes",
                range.first + index * step,
                raw.toStepValue(),
            )
        }
    }

    /**
     * A range whose stops genuinely do not land on exact floats.
     *
     * This is the case the conversion exists for, and unlike the two above it
     * does fail with `toInt()` — so the guard is known to be able to go red.
     */
    @Test
    fun `a range that does not divide evenly still maps back exactly`() {
        val range = 0..49

        stopsOf(range, step = 1).forEachIndexed { index, raw ->
            assertEquals(index, raw.toStepValue())
        }
    }
}
