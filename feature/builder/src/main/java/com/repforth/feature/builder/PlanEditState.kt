package com.repforth.feature.builder

import com.repforth.core.ai.EditablePlan
import com.repforth.core.ai.PlanRef
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.MediaRef

/** One exercise's numbers, as a review row shows them. Exactly one of reps and duration is set. */
data class Prescription(
    val sets: Int,
    val reps: Int?,
    val durationSeconds: Int?,
    val weightKg: Double?,
    val restSeconds: Int,
)

/**
 * One line of Coach's proposed edit, in names rather than ids.
 *
 * [dayNumber] counts from 1 for a week and is null for a single workout, where
 * "Day 1" would be noise.
 */
sealed interface PlanEditRow {
    val dayNumber: Int?

    data class Replaced(
        override val dayNumber: Int?,
        val from: String,
        val to: String,
        val thumbnail: MediaRef,
        val after: Prescription,
    ) : PlanEditRow

    /** [afterName] is the exercise it follows in the plan as sent, or null at the start of the day. */
    data class Added(
        override val dayNumber: Int?,
        val name: String,
        val thumbnail: MediaRef,
        val afterName: String?,
        val prescription: Prescription,
    ) : PlanEditRow

    data class Removed(
        override val dayNumber: Int?,
        val name: String,
        val thumbnail: MediaRef,
    ) : PlanEditRow

    data class Adjusted(
        override val dayNumber: Int?,
        val name: String,
        val thumbnail: MediaRef,
        val before: Prescription,
        val after: Prescription,
    ) : PlanEditRow
}

/** The exercise a request is about, when it was started from that exercise's card. */
data class PinnedExercise(val ref: PlanRef, val name: String, val dayNumber: Int?)

/**
 * Where a Coach plan edit is, as one value rather than a handful of flags that
 * could disagree.
 *
 * [Working] and [Reviewing] carry the `basis` the request was computed from --
 * the exercises as they stood when it was sent -- so a proposal is never applied
 * to a plan it was not computed from. See [BuilderUiState.planEditIsStale].
 */
sealed interface PlanEditState {
    data object Closed : PlanEditState

    data class Asking(val pin: PinnedExercise?, val text: String) : PlanEditState

    data class Working(
        val pin: PinnedExercise?,
        val instruction: String,
        val basis: List<List<ExerciseContent>>,
    ) : PlanEditState

    /**
     * [edited] and [introduced] are what Apply turns into drafts, at Apply and
     * against the state as it is then: a rename made while reading the review
     * must survive it. [dayMinutes] is each touched day's new length, keyed by
     * day number (1 for a single workout).
     */
    data class Reviewing(
        val pin: PinnedExercise?,
        val instruction: String,
        val basis: List<List<ExerciseContent>>,
        val rationale: String,
        val rows: List<PlanEditRow>,
        val dayMinutes: Map<Int, Int>,
        val edited: EditablePlan,
        val introduced: Map<ExerciseId, ExerciseSummary>,
    ) : PlanEditState

    data class Failed(
        val pin: PinnedExercise?,
        val instruction: String,
        val error: CoachError,
    ) : PlanEditState
}
