package com.repforth.core.model

import kotlin.math.roundToLong

/**
 * The numeric shape of an editable workout.
 *
 * The builder, rules engine, and provider validator all accept the same plan.
 * Keeping their limits here prevents a provider response from passing one
 * boundary and then being silently clamped by the next.
 */
object WorkoutLimits {
    val sets = 1..10
    val reps = 1..100
    val durationSeconds = 5..3_600
    val restSeconds = 0..600
    val weightKg = 0.0..500.0

    /** How many training days one generated plan may cover. */
    val days = 1..7

    /**
     * How long one session may be asked to run, in minutes.
     *
     * Here rather than in onboarding, which is where it used to live and is only
     * the first screen that asks. Settings edits it afterwards and Coach
     * overrides it for one generation, so a range owned by the screen that asks
     * first would have been copied twice.
     */
    val sessionMinutes = 15..120

    /** What a control moves session length in. Nobody means 47 minutes. */
    const val sessionMinutesStep = 5

    /**
     * What a *generated* weight is rounded to, in kilograms.
     *
     * Nobody loads 62.5 kg because a model said so. A generated weight is a
     * guess at a working load, and a guess written to a tenth of a kilogram
     * reads as a measurement -- it invites someone to hunt for plates to match
     * a number that was never that precise.
     *
     * **Kilograms, and only kilograms.** §7 stores every weight in kg and
     * converts it for display, so a plan rounded here is round for a reader who
     * chose metric and lands on 55, 77, 99 lb for a reader who chose pounds.
     * Rounding in the reader's unit would make the stored number depend on a
     * display preference, which is the one thing §7 forbids.
     *
     * **A typed weight is never rounded.** Someone who enters 62.5 has 62.5 on
     * the bar; this is about the numbers the app invents, not the ones it is
     * told.
     */
    const val weightKgStep = 5.0

    /**
     * A generated weight, snapped to something a rack can hold.
     *
     * Zero stays zero, because zero is how "no load" arrives when it does not
     * arrive as null, and turning it into 5 kg would put a barbell in someone's
     * hands for a press-up.
     *
     * **Anything else is floored at one step rather than rounded down to
     * nothing.** A 2 kg dumbbell rounds to zero on the nearest-multiple rule,
     * and zero reads as bodyweight -- so the lightest thing this can say is
     * 5 kg, which is wrong by 3 kg where rounding down would have been wrong
     * about what the exercise is.
     *
     * **A weight the contract will reject is returned untouched**, so that
     * rounding cannot rescue it. 502 kg is a model that has misunderstood the
     * question, and snapping it to 500 would hide that from the validator and
     * spend a retry on nothing.
     */
    fun roundWeightKg(kg: Double): Double {
        if (kg !in weightKg) return kg
        if (kg <= 0.0) return 0.0
        return maxOf(weightKgStep, (kg / weightKgStep).roundToLong() * weightKgStep)
    }

    /**
     * The most exercises one day may contain.
     *
     * This is the ceiling the JSON schema and the validator both derive from, so
     * a provider cannot return a day the builder would then have to truncate.
     */
    const val maxExercisesPerDay = 8

    /** The conservative duration estimate used everywhere a rep has no measured tempo. */
    const val secondsPerRepEstimate = 3
}
