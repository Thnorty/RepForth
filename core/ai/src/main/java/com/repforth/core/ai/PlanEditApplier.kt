package com.repforth.core.ai

/** One exercise's place in the **edited** plan, both counted from 0. */
data class ExerciseSlot(val dayIndex: Int, val index: Int)

/**
 * A plan with a model's changes applied, and a record of what they touched.
 *
 * [changed] is every exercise the edit added, replaced or adjusted; [touchedDays]
 * is every day any change landed in, removals included. Both exist for
 * [PlanEditValidator], which judges only what the edit did and not the plan it
 * found.
 */
data class AppliedEdit(
    val plan: EditablePlan,
    val changed: Set<ExerciseSlot>,
    val touchedDays: Set<Int>,
)

sealed interface PlanEditApplication {
    data class Applied(val edit: AppliedEdit) : PlanEditApplication

    data class Rejected(val violations: List<AiWorkoutContractViolation>) : PlanEditApplication
}

/**
 * Applies a model's changes to the plan it was shown.
 *
 * Every address is read against the plan **as sent**, never against the plan
 * part-way through: a removal at position 1 does not make "position 2" mean
 * something else for the next change. That is what makes an answer mean one
 * thing whatever order its changes are listed in, and it is why the whole
 * answer is checked before any of it is applied.
 *
 * Pure. Untouched exercises and untouched days come out as the same instances,
 * so a caller can rely on identity as well as equality.
 */
object PlanEditApplier {

    fun apply(plan: EditablePlan, changes: List<AiPlanChange>): PlanEditApplication {
        if (changes.size > PlanEditLimits.maxChanges) {
            return PlanEditApplication.Rejected(
                listOf(AiWorkoutContractViolation(AiWorkoutIssue.TOO_MANY_CHANGES)),
            )
        }

        val violations = mutableListOf<AiWorkoutContractViolation>()
        changes.forEach { change -> violations += check(plan, change) }

        changes
            .filter { it.op != PlanEditOp.ADD }
            .groupBy { it.day to it.position }
            .filterValues { it.size > 1 }
            .keys
            .forEach { (day, position) ->
                violations += AiWorkoutContractViolation(
                    AiWorkoutIssue.CONFLICTING_CHANGES,
                    dayIndex = day - 1,
                    position = position,
                )
            }

        if (violations.isNotEmpty()) return PlanEditApplication.Rejected(violations)

        val changed = mutableSetOf<ExerciseSlot>()
        val touched = changes.map { it.day - 1 }.toSet()
        val days = plan.days.mapIndexed { dayIndex, day ->
            if (dayIndex !in touched) return@mapIndexed day
            val dayChanges = changes.filter { it.day - 1 == dayIndex }
            val exercises = buildList {
                fun emitAddsAfter(position: Int) = dayChanges
                    .filter { it.op == PlanEditOp.ADD && it.position == position }
                    .forEach {
                        changed += ExerciseSlot(dayIndex, size)
                        add(it.introduced())
                    }

                emitAddsAfter(0)
                day.exercises.forEachIndexed { index, original ->
                    val position = index + 1
                    val change = dayChanges.firstOrNull {
                        it.op != PlanEditOp.ADD && it.position == position
                    }
                    when (change?.op) {
                        null -> add(original)
                        PlanEditOp.REMOVE -> Unit
                        PlanEditOp.REPLACE -> {
                            changed += ExerciseSlot(dayIndex, size)
                            add(change.introduced())
                        }
                        PlanEditOp.ADJUST -> {
                            changed += ExerciseSlot(dayIndex, size)
                            add(original.adjustedBy(change))
                        }
                        PlanEditOp.ADD -> error("filtered above")
                    }
                    emitAddsAfter(position)
                }
            }
            if (exercises.isEmpty()) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.EMPTY_PLAN, dayIndex = dayIndex)
            }
            day.copy(exercises = exercises)
        }

        if (violations.isNotEmpty()) return PlanEditApplication.Rejected(violations)
        return PlanEditApplication.Applied(
            AppliedEdit(plan = EditablePlan(days), changed = changed, touchedDays = touched),
        )
    }

    /** Whether one change names a real place and carries exactly its operation's fields. */
    private fun check(plan: EditablePlan, change: AiPlanChange): List<AiWorkoutContractViolation> {
        fun violation(issue: AiWorkoutIssue) = AiWorkoutContractViolation(
            issue = issue,
            dayIndex = change.day - 1,
            exerciseId = change.exerciseId,
            position = change.position,
        )

        val day = plan.days.getOrNull(change.day - 1)
        val validPositions = when {
            day == null -> IntRange.EMPTY
            change.op == PlanEditOp.ADD -> 0..day.exercises.size
            else -> 1..day.exercises.size
        }
        if (change.position !in validPositions) return listOf(violation(AiWorkoutIssue.UNKNOWN_POSITION))

        if (!change.hasShapeOf(change.op)) return listOf(violation(AiWorkoutIssue.OPERATION_SHAPE))

        if (change.op == PlanEditOp.ADJUST) {
            val target = day!!.exercises[change.position - 1]
            val timed = target.durationSeconds != null
            if ((timed && change.repetitions != null) || (!timed && change.durationSeconds != null)) {
                return listOf(violation(AiWorkoutIssue.TARGET_TYPE_MISMATCH))
            }
        }
        return emptyList()
    }

    private fun AiPlanChange.hasShapeOf(op: PlanEditOp): Boolean = when (op) {
        // Exactly-one-of repetitions/duration is the validator's TARGET_SHAPE,
        // which already has a sentence the model can act on; this only asks
        // whether the operation's own fields are present.
        PlanEditOp.REPLACE, PlanEditOp.ADD ->
            exerciseId != null && sets != null && restSeconds != null
        PlanEditOp.REMOVE ->
            exerciseId == null && sets == null && repetitions == null &&
                durationSeconds == null && weightKg == null && restSeconds == null
        PlanEditOp.ADJUST ->
            exerciseId == null && listOf(sets, repetitions, durationSeconds, weightKg, restSeconds)
                .any { it != null }
    }

    private fun AiPlanChange.introduced() = EditableExercise(
        key = null,
        exerciseId = requireNotNull(exerciseId),
        sets = requireNotNull(sets),
        repetitions = repetitions,
        durationSeconds = durationSeconds,
        weightKg = weightKg,
        restSeconds = requireNotNull(restSeconds),
    )

    private fun EditableExercise.adjustedBy(change: AiPlanChange) = copy(
        sets = change.sets ?: sets,
        repetitions = change.repetitions ?: repetitions,
        durationSeconds = change.durationSeconds ?: durationSeconds,
        weightKg = change.weightKg ?: weightKg,
        restSeconds = change.restSeconds ?: restSeconds,
    )
}
