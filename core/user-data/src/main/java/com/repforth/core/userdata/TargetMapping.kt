package com.repforth.core.userdata

import com.repforth.core.model.ExerciseTarget

/**
 * Rebuilds an [ExerciseTarget] from the columns two tables store it in.
 *
 * `template_exercise` and `session_exercise` hold the same four values, so the
 * rule for turning them back into a target lives here once rather than in each
 * repository. It is a rule, not a conversion: the schema can express states the
 * domain type refuses, and this decides what to do about them.
 *
 * Reps wins when a row somehow has both. Arbitrary but total — the alternative
 * is throwing, and one malformed row would then hide a whole plan or a whole
 * workout. Neither present is read as a single rep, for the same reason: the
 * exercise is recoverable, the exception is not.
 */
internal fun exerciseTargetOf(
    sets: Int,
    reps: Int?,
    durationMs: Long?,
    weightKg: Double?,
): ExerciseTarget = when {
    reps != null -> ExerciseTarget.Reps(sets, reps, weightKg)
    durationMs != null -> ExerciseTarget.Duration(sets, durationMs, weightKg)
    else -> ExerciseTarget.Reps(sets, reps = 1, weightKg = weightKg)
}
