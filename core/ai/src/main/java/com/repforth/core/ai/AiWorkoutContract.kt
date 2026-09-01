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
 * How much of the session the answer is expected to use.
 *
 * Every other number in this contract is a ceiling, and a model asked only for
 * ceilings minimises: told "keep each day at or under 2700 seconds", "at most 8
 * exercises per day, at least 1", and that going over is rejected, the safest
 * answer it can give is three exercises and a short rest. That is what it gave
 * — a seven-day week where every day ran eight minutes against a forty-five
 * minute session, and the whole week totalled 56 minutes. Nothing was wrong
 * with it by any rule the app had.
 *
 * So the session length is stated as a target band rather than a cap, and the
 * prompt tells the model to do the app's arithmetic rather than judge by how
 * long a session takes in a real gym — the app's formula counts only work and
 * rest, so the two disagree by roughly 40% and the model was trusting its own.
 *
 * The band is an aim, not a quota. Only [MIN_WEEK_FRACTION] is enforced, and it
 * sits far below: the maintainer's decision is that the ceiling is the contract
 * and how long a day should be is the coach's judgement, so a week the app
 * merely disagrees with must still be allowed through.
 */
object AiPlanFill {
    /**
     * The bottom of the band the prompt asks a day to land in, as a fraction of
     * the session. Above the enforced floor, so an honest attempt is never
     * rejected for missing the target by a little.
     */
    const val TARGET_DAY_FRACTION = 0.8

    /**
     * The floor [AiWorkoutValidator] enforces, across the week rather than per
     * day, and deliberately far below [TARGET_DAY_FRACTION].
     *
     * It is a safety net against the budget being ignored outright, not a
     * quota. How long a day should be is the coach's judgement — a light day, a
     * deload, a short session between two hard ones are all things a week is
     * supposed to be able to contain, and the maintainer's decision is that the
     * app must not overrule them. The session length is a ceiling; this only
     * catches an answer that used almost none of it, which is the failure that
     * prompted it: a seven-day week that came back at 18% of its budget.
     *
     * Per day rather than per week would forbid the light day directly, which is
     * why it is not per day.
     */
    const val MIN_WEEK_FRACTION = 0.3
}

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
