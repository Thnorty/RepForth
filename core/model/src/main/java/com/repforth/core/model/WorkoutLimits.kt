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

    const val maxExercises = 10

    /** The conservative duration estimate used everywhere a rep has no measured tempo. */
    const val secondsPerRepEstimate = 3
}
