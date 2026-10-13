package com.repforth.core.ai

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutLimits

/**
 * One day's length by the app's own estimate -- the formula the prompt states
 * and the builder shows -- not a second copy of it.
 *
 * Never throws. [ExerciseTarget] refuses a non-positive count, and this runs over
 * whatever a hand-built plan holds; the values are floored at 1 so an odd
 * untouched row costs a second rather than taking the edit down.
 */
internal fun EditableDay.estimatedDurationMs(): Long = exercises.sumOf { exercise ->
    val sets = exercise.sets.coerceAtLeast(1)
    PlannedExercise(
        id = exercise.key ?: exercise.exerciseId,
        exerciseId = ExerciseId(exercise.exerciseId),
        position = 0,
        target = exercise.repetitions?.let { reps ->
            ExerciseTarget.Reps(sets = sets, reps = reps.coerceAtLeast(1), weightKg = exercise.weightKg)
        } ?: ExerciseTarget.Duration(
            sets = sets,
            durationMs = (exercise.durationSeconds ?: 0).coerceAtLeast(1) * 1_000L,
            weightKg = exercise.weightKg,
        ),
        restMs = exercise.restSeconds.coerceAtLeast(0) * 1_000L,
    ).estimatedDurationMs
}

/**
 * Judges an applied plan edit on **what the edit introduced**, never on the plan
 * it found.
 *
 * The plan being edited may already break rules a model must not: an exercise
 * added by hand for equipment since removed from the profile, a duplicate typed
 * in by hand, or a day over the session ceiling, which the builder allows with a
 * warning. Rejecting an edit for any of those would make the feature fail on
 * exactly the plans people build themselves -- so each check is aimed at the
 * exercises the edit changed, or at a day only when the edit made it worse.
 *
 * None of the generation-only checks apply. The day count and the week-fill
 * floor judge a week the model wrote; this week is the person's.
 *
 * **Neither does the session length.** An edit may run a day past it -- the
 * owner's decision, 2026-10-13. "Add more chest" on a full day should add chest;
 * refusing it, or quietly answering with a swap, overrules the person on the one
 * thing they asked for. The review shows each touched day's new length and says
 * when it is over, and the builder already warns about a long day, so the cost
 * is visible before Apply rather than enforced after it.
 */
object PlanEditValidator {

    fun validate(
        original: EditablePlan,
        edit: AppliedEdit,
        rationale: String,
        offered: Map<String, ExerciseCandidate>,
    ): List<AiWorkoutContractViolation> = buildList {
        if (rationale.isBlank()) add(AiWorkoutContractViolation(AiWorkoutIssue.RATIONALE_MISSING))

        edit.changed.sortedWith(compareBy({ it.dayIndex }, { it.index })).forEach { slot ->
            val exercise = edit.plan.days[slot.dayIndex].exercises[slot.index]
            val introduced = exercise.key == null
            val candidate = offered[exercise.exerciseId]
            if (introduced && candidate == null) {
                add(violation(AiWorkoutIssue.EXERCISE_NOT_OFFERED, slot.dayIndex, exercise.exerciseId))
            }
            prescriptionIssues(
                sets = exercise.sets,
                repetitions = exercise.repetitions,
                durationSeconds = exercise.durationSeconds,
                weightKg = exercise.weightKg,
                restSeconds = exercise.restSeconds,
                // An adjusted exercise keeps the measure it had; the applier
                // already refused an adjust that switched it.
                isTimed = if (introduced) candidate?.isTimed else exercise.durationSeconds != null,
            ).forEach { add(violation(it, slot.dayIndex, exercise.exerciseId)) }
        }

        // The same order generation keeps: what each exercise says first, and
        // what a day adds up to only once every exercise in it is well-formed.
        // A day estimated from a malformed row is a number about nothing, and a
        // repair prompt that blamed the day for one bad rest period would send
        // the model to cut the wrong thing.
        if (isNotEmpty()) return@buildList

        edit.touchedDays.sorted().forEach { dayIndex ->
            val before = original.days[dayIndex]
            val after = edit.plan.days[dayIndex]
            val changedHere = edit.changed.filter { it.dayIndex == dayIndex }.map { it.index }.toSet()

            after.exercises
                .withIndex()
                .groupBy { it.value.exerciseId }
                .filterValues { copies -> copies.size > 1 && copies.any { it.index in changedHere } }
                .keys
                .forEach { add(violation(AiWorkoutIssue.DUPLICATE_EXERCISE, dayIndex, it)) }

            val count = after.exercises.size
            if (count > WorkoutLimits.maxExercisesPerDay && count > before.exercises.size) {
                add(AiWorkoutContractViolation(AiWorkoutIssue.TOO_MANY_EXERCISES, dayIndex = dayIndex))
            }
        }
    }

    private fun violation(issue: AiWorkoutIssue, dayIndex: Int, exerciseId: String) =
        AiWorkoutContractViolation(issue = issue, dayIndex = dayIndex, exerciseId = exerciseId)
}
