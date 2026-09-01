package com.repforth.core.ai

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.Language
import com.repforth.core.rules.GenerationRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val AI_WORKOUT_SCHEMA_VERSION = 4

/**
 * What the provider is told, in locally trusted types (§8).
 *
 * Two rules decide what belongs here, and both were arrived at by measuring the
 * payload rather than by taste:
 *
 * **Nothing the local filter already applied.** [com.repforth.core.rules.RulesEngine]
 * removes excluded exercises, excluded muscles and unavailable equipment from
 * the catalog before this is built, so listing them again told the model to
 * avoid things it could not see. Three fields left for that reason
 * (`excluded_exercise_ids`, `excluded_muscles`, `equipment`) and the request got
 * shorter and truer at the same time. [excludedMovements] stays precisely
 * because it is the one exclusion the catalog filter could not express — and it
 * is now enforced locally too, rather than only asked for.
 *
 * **Everything selection actually needs.** The earlier version carried no
 * exercise names, which read as a privacy measure and was not one: names are
 * public catalog data and the ids already identify them exactly. What it
 * actually did was make 1,265 of the catalog's 1,324 exercises indistinguishable
 * from some other exercise on the wire — 89 of them arrived as the identical
 * `{abs, body weight, repetitions}` — so choosing between them was arbitrary and
 * no instruction about warm-ups or ordering could be acted on. See
 * [AiExerciseCandidate].
 *
 * This type is not serialised. The catalog goes out as a delimited table (see
 * [toGenerationPrompt]), which is both richer and smaller than the JSON array of
 * objects it replaced.
 */
data class AiWorkoutRequest(
    val locale: String,
    val goal: String,
    val experience: String,
    val days: Int,
    val sessionDurationMinutes: Int,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    /**
     * Free-text movement patterns the user must not be programmed.
     *
     * Still sent, even though the catalog filter now applies it by name, because
     * substring matching catches `overhead press` and not `push press`. Telling
     * the model as well is the cheap half of a constraint that cannot be made
     * exact.
     */
    val excludedMovements: List<String>,
    val candidates: List<AiExerciseCandidate>,
) {
    companion object {
        /** Builds the request from locally trusted types, in stable order. */
        fun from(
            request: GenerationRequest,
            locale: Language,
            eligibleCandidates: List<ExerciseCandidate>,
        ): AiWorkoutRequest {
            val primary = request.targetMuscles.canonicalSlugs()
            return AiWorkoutRequest(
                locale = locale.tag,
                goal = request.profile.goal.name.lowercase(),
                experience = request.profile.experience.name.lowercase(),
                days = request.days,
                sessionDurationMinutes = (request.sessionLengthMs / 60_000L).toInt(),
                primaryMuscles = primary,
                secondaryMuscles = request.profile.preferredMuscles
                    .canonicalSlugs()
                    .filterNot(primary::contains),
                excludedMovements = request.excludedMovements.sorted(),
                candidates = eligibleCandidates
                    .map(AiExerciseCandidate::from)
                    // Grouped by muscle, then by name. The filter sorts by id
                    // for stability, which is the right default for a set about
                    // to be compared and the wrong one for a set about to be
                    // read: a model choosing chest work should find every chest
                    // option in one contiguous run rather than scattered through
                    // a list ordered by upstream row number.
                    .sortedWith(compareBy({ it.target }, { it.name }, { it.id })),
            )
        }
    }
}

/**
 * One catalog exercise as the model sees it.
 *
 * [name] is the field that makes the rest mean anything. Without it the model
 * receives a bucket of interchangeable ids and can only contribute volume and
 * prescription; with it, "open with mobility work and close with stretches"
 * becomes an instruction it can follow, because `assisted lying calves stretch`
 * says what it is.
 *
 * [equipment] survives that addition rather than being folded into the name:
 * 40% of catalog names do not contain their equipment — nearly every body-weight
 * and machine exercise — and the model needs it to decide whether `weight_kg`
 * means anything.
 *
 * [secondaryMuscles] is what lets a week be a week. Spacing the same muscle
 * across days is impossible when every exercise claims exactly one.
 */
data class AiExerciseCandidate(
    val id: String,
    val name: String,
    val target: String,
    val secondaryMuscles: List<String>,
    val equipment: String,
    val timed: Boolean,
) {
    /** One row of [CATALOG_HEADER]. See it for the column meanings. */
    fun toCatalogRow(): String = listOf(
        id,
        name,
        target,
        secondaryMuscles.joinToString(","),
        equipment,
        if (timed) "T" else "R",
    ).joinToString("|")

    companion object {
        /**
         * The catalog's column names, sent once above the table.
         *
         * A table rather than an array of JSON objects, because repeating field
         * names on every row was most of what the request weighed. Measured over
         * all 1,324 catalog exercises: the JSON form was 112,110 characters
         * carrying four fields each; this form is 90,205 carrying six. Around
         * 5,500 tokens cheaper *and* strictly more informative, which is how
         * names could be added without the request growing.
         */
        const val CATALOG_HEADER =
            "id|name|target_muscle|secondary_muscles|equipment|R=reps,T=timed"

        fun from(candidate: ExerciseCandidate) = AiExerciseCandidate(
            id = candidate.id.value,
            name = candidate.name,
            target = candidate.target.canonical.slug,
            secondaryMuscles = candidate.secondaryMuscles
                .map { it.canonical.slug }
                .distinct()
                .filterNot { it == candidate.target.canonical.slug }
                .sorted(),
            equipment = candidate.equipment.slug,
            timed = candidate.isTimed,
        )
    }
}

/**
 * The provider response before it is trusted or shown.
 *
 * Every field the model no longer has to produce is a way the generation can no
 * longer fail, and four went for that reason:
 *
 * - `schema_version` — a constant this app had just sent, echoed back. It could
 *   only confirm what was already known, or fail a whole week because a model
 *   mistyped a number it had been given.
 * - `day_index` and `order` — array position already says both, unambiguously.
 *   Asking for them again created two contract violations (`DAY_INDEX_ORDER`,
 *   `ORDER`) that existed only to check the model against a fact the JSON
 *   structure cannot get wrong.
 * - `tempo` — generated, validated, normalised, and read by nothing.
 */
@Serializable
data class AiWorkoutResponse(
    val days: List<AiPlannedDay>,
    val rationale: String,
)

@Serializable
data class AiPlannedDay(
    val title: String,
    @SerialName("focus_muscles") val focusMuscles: List<String> = emptyList(),
    val exercises: List<AiPlannedExercise>,
)

@Serializable
data class AiPlannedExercise(
    @SerialName("exercise_id") val exerciseId: String,
    val sets: Int,
    val repetitions: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("rest_seconds") val restSeconds: Int,
)

sealed interface AiWorkoutDecodeResult {
    data class Ok(val response: AiWorkoutResponse) : AiWorkoutDecodeResult

    /** No raw exception text: a decoder message can quote the provider response. */
    data object Malformed : AiWorkoutDecodeResult
}

/**
 * The strict codec for the model-authored payload.
 *
 * Provider envelopes are intentionally forward-compatible and ignore unknown
 * fields. The structured workout inside one is our versioned contract, so an
 * unknown field is evidence that the model did not follow that contract.
 */
object AiWorkoutCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
    }

    fun decodeResponse(value: String): AiWorkoutDecodeResult = try {
        AiWorkoutDecodeResult.Ok(json.decodeFromString<AiWorkoutResponse>(value))
    } catch (_: SerializationException) {
        AiWorkoutDecodeResult.Malformed
    } catch (_: IllegalArgumentException) {
        AiWorkoutDecodeResult.Malformed
    }
}

private fun Iterable<com.repforth.core.model.Muscle>.canonicalSlugs(): List<String> =
    map { it.canonical.slug }.distinct().sorted()
