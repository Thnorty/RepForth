package com.repforth.feature.builder

import com.repforth.core.ai.AiPlanChange
import com.repforth.core.ai.AiPlannedExercise
import com.repforth.core.ai.EditableDay
import com.repforth.core.ai.EditableExercise
import com.repforth.core.ai.EditablePlan
import com.repforth.core.ai.PlanEditOp
import com.repforth.core.ai.PlanRef
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.MediaRef

/*
 * Between the builder's drafts and Coach's edit contract.
 *
 * A single workout travels as a plan of one day, titled with the plan's name --
 * the same convention generation uses on the wire. Every exercise is keyed by
 * its DraftExercise.id, which is what lets the way back keep the person's own
 * rows: an exercise the edit only adjusted keeps its identity, the target half
 * it is not using, and the progression carry it has built up.
 */

/** The days being edited: the week's, or the standalone workout as one day. */
private val BuilderUiState.draftDays: List<Pair<String, List<DraftExercise>>>
    get() = if (weekDays.isNotEmpty()) {
        weekDays.map { it.title to it.exercises }
    } else {
        listOf(name to exercises)
    }

internal fun BuilderUiState.toEditablePlan(): EditablePlan = EditablePlan(
    draftDays.map { (title, drafts) -> EditableDay(title, drafts.map { it.toEditable() }) },
)

private fun DraftExercise.toEditable() = EditableExercise(
    key = id,
    exerciseId = exerciseId.value,
    sets = sets,
    repetitions = reps.takeUnless { timed },
    durationSeconds = durationSeconds.takeIf { timed },
    weightKg = weightKg,
    restSeconds = restSeconds,
)

/**
 * What a proposal was computed from: the exercises, day by day, and nothing
 * else. Names and day titles are left out on purpose -- renaming the plan while
 * reading Coach's suggestions does not make them wrong, and Apply keeps the new
 * name.
 */
internal val BuilderUiState.planEditBasis: List<List<ExerciseContent>>
    get() = draftDays.map { (_, drafts) -> drafts.map { it.contentKey() } }

/**
 * This state with [edited]'s exercises in place of its own, and everything else
 * -- names, day titles, template ids, weekdays, notes -- as it is now.
 *
 * [introduced] names the exercises the edit brought in; every other exercise is
 * found by its key among the current drafts.
 */
internal fun BuilderUiState.withEditedPlan(
    edited: EditablePlan,
    introduced: Map<ExerciseId, ExerciseSummary>,
): BuilderUiState {
    val byId = (exercises + weekDays.flatMap { it.exercises }).associateBy { it.id }
    fun resolve(exercise: EditableExercise): DraftExercise {
        val original = exercise.key?.let(byId::get)
            ?: return listOf(exercise.toPlanned()).toAiDrafts(introduced).single()
        if (original.toEditable() == exercise) return original
        return original.copy(
            sets = exercise.sets,
            reps = exercise.repetitions ?: original.reps,
            durationSeconds = exercise.durationSeconds ?: original.durationSeconds,
            weightKg = exercise.weightKg,
            restSeconds = exercise.restSeconds,
        )
    }
    return if (weekDays.isNotEmpty()) {
        copy(
            weekDays = weekDays.mapIndexed { index, day ->
                val editedDay = edited.days[index]
                day.copy(exercises = editedDay.exercises.map(::resolve))
            },
        )
    } else {
        copy(exercises = edited.days.single().exercises.map(::resolve))
    }
}

private fun EditableExercise.toPlanned() = AiPlannedExercise(
    exerciseId = exerciseId,
    sets = sets,
    repetitions = repetitions,
    durationSeconds = durationSeconds,
    weightKg = weightKg,
    restSeconds = restSeconds,
)

/** The review's lines, one per change, in the order the model gave them, read against this state. */
internal fun BuilderUiState.reviewRows(
    changes: List<AiPlanChange>,
    introduced: Map<ExerciseId, ExerciseSummary>,
): List<PlanEditRow> {
    val days = draftDays.map { it.second }
    val isWeek = weekDays.isNotEmpty()
    return changes.map { change ->
        val dayNumber = change.day.takeIf { isWeek }
        val day = days[change.day - 1]
        val at = day.getOrNull(change.position - 1)
        val newName = change.exerciseId?.let { id -> introduced[ExerciseId(id)]?.name ?: id }
        val newThumbnail = change.exerciseId
            ?.let { id -> introduced[ExerciseId(id)]?.thumbnail }
            ?: MediaRef.Unavailable
        when (change.op) {
            PlanEditOp.REPLACE -> PlanEditRow.Replaced(
                dayNumber, requireNotNull(at).name, requireNotNull(newName), newThumbnail, change.prescription(),
            )
            PlanEditOp.ADD -> PlanEditRow.Added(
                dayNumber, requireNotNull(newName), newThumbnail, at?.name, change.prescription(),
            )
            PlanEditOp.REMOVE -> PlanEditRow.Removed(dayNumber, requireNotNull(at).name, at.thumbnail)
            PlanEditOp.ADJUST -> {
                val before = requireNotNull(at).prescription()
                PlanEditRow.Adjusted(
                    dayNumber,
                    at.name,
                    at.thumbnail,
                    before = before,
                    after = Prescription(
                        sets = change.sets ?: before.sets,
                        reps = change.repetitions ?: before.reps,
                        durationSeconds = change.durationSeconds ?: before.durationSeconds,
                        weightKg = change.weightKg ?: before.weightKg,
                        restSeconds = change.restSeconds ?: before.restSeconds,
                    ),
                )
            }
        }
    }
}

private fun AiPlanChange.prescription() = Prescription(
    sets = requireNotNull(sets),
    reps = repetitions,
    durationSeconds = durationSeconds,
    weightKg = weightKg,
    restSeconds = requireNotNull(restSeconds),
)

private fun DraftExercise.prescription() = Prescription(
    sets = sets,
    reps = reps.takeUnless { timed },
    durationSeconds = durationSeconds.takeIf { timed },
    weightKg = weightKg,
    restSeconds = restSeconds,
)

/** The exercise at [ref], named for the request's chip, or null if there is none there. */
internal fun BuilderUiState.pinnedAt(ref: PlanRef): PinnedExercise? {
    val draft = draftDays.getOrNull(ref.day - 1)?.second?.getOrNull(ref.position - 1) ?: return null
    return PinnedExercise(ref, draft.name, ref.day.takeIf { weekDays.isNotEmpty() })
}
