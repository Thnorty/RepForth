package com.repforth.core.model

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
     * The most exercises one day may contain.
     *
     * This is the ceiling the JSON schema and the validator both derive from, so
     * a provider cannot return a day the builder would then have to truncate.
     */
    const val maxExercisesPerDay = 8

    /** The conservative duration estimate used everywhere a rep has no measured tempo. */
    const val secondsPerRepEstimate = 3
}
