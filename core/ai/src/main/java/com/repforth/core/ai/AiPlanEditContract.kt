package com.repforth.core.ai

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.Language
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WorkoutLimits
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

const val AI_PLAN_EDIT_SCHEMA_VERSION = 1

/** The provider-visible name changes when the contract changes. */
const val AI_PLAN_EDIT_SCHEMA_NAME = "repforth_plan_edit_v$AI_PLAN_EDIT_SCHEMA_VERSION"

/**
 * The bounds of one plan edit, beside [WorkoutLimits] rather than inside it:
 * these describe a request to change a plan, not the plan.
 */
object PlanEditLimits {
    /**
     * How many changes one answer may make.
     *
     * An edit is a handful of swaps and additions. An answer that changes more
     * than this has rewritten the plan, which is what generation is for, and a
     * review list that long is one nobody reads before pressing Apply.
     */
    const val maxChanges = 12

    /**
     * How much the person may type. Enough for any request; not enough to paste
     * a whole programme into the prompt and pay for it on every edit.
     */
    const val maxInstructionChars = 500
}

/**
 * One exercise's address in the plan **as it was sent**: day and position, both
 * counted from 1, exactly as the model reads and writes them.
 *
 * Positions never refer to the plan part-way through applying an answer. That
 * is what lets a removal and an addition in the same answer agree about what
 * "position 3" means.
 */
data class PlanRef(val day: Int, val position: Int)

/** A plan as an edit sees it: days of exercises, whatever the builder calls them. */
data class EditablePlan(val days: List<EditableDay>)

data class EditableDay(val title: String, val exercises: List<EditableExercise>)

/**
 * One exercise in an [EditablePlan].
 *
 * [key] is the caller's identity for the exercise, carried through untouched so
 * the caller can tell which of its own rows survived an edit. It is null for an
 * exercise the edit introduced, which is how the validator knows what to judge.
 *
 * Exactly one of [repetitions] and [durationSeconds] is set, as on the wire.
 */
data class EditableExercise(
    val key: String?,
    val exerciseId: String,
    val sets: Int,
    val repetitions: Int?,
    val durationSeconds: Int?,
    val weightKg: Double?,
    val restSeconds: Int,
)

@Serializable
enum class PlanEditOp {
    @SerialName("replace") REPLACE,
    @SerialName("add") ADD,
    @SerialName("remove") REMOVE,
    @SerialName("adjust") ADJUST,
}

/**
 * One change a model proposes.
 *
 * Flat, with the same fields for every [op], rather than one shape per
 * operation: structured output handles nullable fields reliably, and a schema
 * of alternative shapes is where Gemini's support is unproven. Which fields an
 * operation may set is checked by [PlanEditApplier], not by the decoder.
 *
 * No defaults on purpose. Every field is required on the wire, null included, so
 * a missing one is a contract failure rather than a silent null.
 */
@Serializable
data class AiPlanChange(
    val op: PlanEditOp,
    val day: Int,
    val position: Int,
    @SerialName("exercise_id") val exerciseId: String?,
    val sets: Int?,
    val repetitions: Int?,
    @SerialName("duration_seconds") val durationSeconds: Int?,
    @SerialName("weight_kg") val weightKg: Double?,
    @SerialName("rest_seconds") val restSeconds: Int?,
)

/** The model's whole answer. An empty [changes] is a valid "change nothing", explained. */
@Serializable
data class AiPlanEditResponse(
    val changes: List<AiPlanChange>,
    val rationale: String,
)

sealed interface AiPlanEditDecodeResult {
    data class Ok(val response: AiPlanEditResponse) : AiPlanEditDecodeResult

    /** No exception text: a decoder message can quote the provider response. */
    data object Malformed : AiPlanEditDecodeResult
}

/** The strict codec for a model-authored plan edit. See [strictContractJson]. */
object AiPlanEditCodec {
    fun decodeResponse(value: String): AiPlanEditDecodeResult = try {
        AiPlanEditDecodeResult.Ok(
            strictContractJson.decodeFromString<AiPlanEditResponse>(value).onTheGrid(),
        )
    } catch (_: SerializationException) {
        AiPlanEditDecodeResult.Malformed
    } catch (_: IllegalArgumentException) {
        AiPlanEditDecodeResult.Malformed
    }
}

/** Weights and durations on their step, at the decoder, for the reason `AiWorkoutCodec` gives. */
private fun AiPlanEditResponse.onTheGrid() = copy(
    changes = changes.map { change ->
        change.copy(
            weightKg = change.weightKg?.let(WorkoutLimits::roundWeightKg),
            durationSeconds = change.durationSeconds?.let(WorkoutLimits::roundDurationSeconds),
        )
    },
)

/**
 * What the person asked for, against the plan they asked it of.
 *
 * Goal and experience come from [profile]: the edit sheet has no override
 * controls, because an edit changes a plan rather than who it is for.
 */
data class PlanEditRequest(
    val profile: UserProfile,
    val plan: EditablePlan,
    val instruction: String,
    /** The exercise the person pointed at, when they started from its card. */
    val focus: PlanRef?,
)

/**
 * One exercise of the current plan as the model reads it, addressed by [ref] so
 * a change can name it. See [toRow] for the format.
 */
data class AiPlanRow(
    val ref: PlanRef,
    val exerciseId: String,
    val name: String,
    val target: String,
    val sets: Int,
    val repetitions: Int?,
    val durationSeconds: Int?,
    val weightKg: Double?,
    val restSeconds: Int,
) {
    /** One row under [PLAN_HEADER]. */
    fun toRow(): String = listOf(
        "${ref.day}.${ref.position}",
        exerciseId,
        name,
        target,
        sets.toString(),
        repetitions?.let { "$it reps" } ?: "${durationSeconds}s",
        weightKg?.let(::plainNumber).orEmpty(),
        restSeconds.toString(),
    ).joinToString("|")

    companion object {
        const val PLAN_HEADER = "ref|exercise_id|name|target_muscle|sets|target|weight_kg|rest_seconds"

        /** `60`, not `60.0`: the row is read, and a trailing zero reads as precision. */
        private fun plainNumber(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
}

/** The plan-edit request in locally trusted types, ready to become a prompt. */
data class AiPlanEditRequest(
    val locale: String,
    val goal: String,
    val experience: String,
    val sessionDurationMinutes: Int,
    val instruction: String,
    val focus: PlanRef?,
    val dayTitles: List<String>,
    val rows: List<AiPlanRow>,
    val candidates: List<AiExerciseCandidate>,
) {
    companion object {
        /**
         * [catalog] is the whole catalog, used only to name the plan's own rows --
         * an exercise added by hand may be one the profile's equipment no longer
         * reaches, and it still needs a name. [eligible] is what the model may
         * choose from.
         *
         * A row whose exercise has left the catalog entirely is sent with its id
         * as its name and `unknown` as its muscle rather than dropped: dropping it
         * would renumber the day and every position after it would be wrong.
         */
        fun from(
            request: PlanEditRequest,
            locale: Language,
            catalog: List<ExerciseCandidate>,
            eligible: List<ExerciseCandidate>,
        ): AiPlanEditRequest {
            val known = catalog.associateBy { it.id.value }
            return AiPlanEditRequest(
                locale = locale.tag,
                goal = request.profile.goal.name.lowercase(),
                experience = request.profile.experience.name.lowercase(),
                sessionDurationMinutes = (request.profile.sessionLengthMs / 60_000L).toInt(),
                instruction = request.instruction.take(PlanEditLimits.maxInstructionChars),
                focus = request.focus,
                dayTitles = request.plan.days.map { it.title },
                rows = request.plan.days.flatMapIndexed { dayIndex, day ->
                    day.exercises.mapIndexed { index, exercise ->
                        val candidate = known[exercise.exerciseId]
                        AiPlanRow(
                            ref = PlanRef(day = dayIndex + 1, position = index + 1),
                            exerciseId = exercise.exerciseId,
                            name = candidate?.name ?: exercise.exerciseId,
                            target = candidate?.target?.canonical?.slug ?: "unknown",
                            sets = exercise.sets,
                            repetitions = exercise.repetitions,
                            durationSeconds = exercise.durationSeconds,
                            weightKg = exercise.weightKg,
                            restSeconds = exercise.restSeconds,
                        )
                    }
                },
                // Ordered as generation orders them, so the model finds every
                // option for a muscle in one run.
                candidates = eligible
                    .map(AiExerciseCandidate::from)
                    .sortedWith(compareBy({ it.target }, { it.name }, { it.id })),
            )
        }
    }
}
