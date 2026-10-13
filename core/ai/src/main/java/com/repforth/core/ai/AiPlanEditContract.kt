package com.repforth.core.ai

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
