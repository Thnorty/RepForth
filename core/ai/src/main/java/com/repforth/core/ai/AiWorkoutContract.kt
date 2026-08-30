package com.repforth.core.ai

import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.Language
import com.repforth.core.rules.GenerationRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val AI_WORKOUT_SCHEMA_VERSION = 2

/**
 * The compact, language-neutral request sent to a provider (§8).
 *
 * It deliberately carries no exercise names, instructions, profile id, or
 * stored settings. The provider gets only the constraints and the locally
 * filtered choices it needs to arrange a plan.
 */
@Serializable
data class AiWorkoutRequest(
    @SerialName("schema_version") val schemaVersion: Int,
    val locale: String,
    val goal: String,
    val experience: String,
    @SerialName("primary_muscles") val primaryMuscles: List<String>,
    @SerialName("secondary_muscles") val secondaryMuscles: List<String>,
    @SerialName("excluded_muscles") val excludedMuscles: List<String>,
    @SerialName("excluded_exercise_ids") val excludedExerciseIds: List<String>,
    @SerialName("excluded_movements") val excludedMovements: List<String>,
    val equipment: List<String>,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("candidate_exercises") val candidateExercises: List<AiExerciseCandidate>,
) {
    companion object {
        /** Builds the wire request from locally trusted types, in stable order. */
        fun from(
            request: GenerationRequest,
            locale: Language,
            eligibleCandidates: List<ExerciseCandidate>,
        ): AiWorkoutRequest {
            val primary = request.targetMuscles.canonicalSlugs()
            return AiWorkoutRequest(
                schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
                locale = locale.tag,
                goal = request.profile.goal.name.lowercase(),
                experience = request.profile.experience.name.lowercase(),
                primaryMuscles = primary,
                secondaryMuscles = request.profile.preferredMuscles
                    .canonicalSlugs()
                    .filterNot(primary::contains),
                excludedMuscles = request.excludedMuscles.canonicalSlugs(),
                excludedExerciseIds = request.excludedExerciseIds
                    .map { it.value }
                    .sorted(),
                excludedMovements = request.profile.exclusions
                    .asSequence()
                    .filter { it.kind == ExclusionKind.MOVEMENT }
                    .map { it.value.trim() }
                    .sorted()
                    .toList(),
                equipment = request.availableEquipment.map { it.slug }.distinct().sorted(),
                durationMinutes = (request.sessionLengthMs / 60_000L).toInt(),
                candidateExercises = eligibleCandidates
                    .sortedBy { it.id.value }
                    .map(AiExerciseCandidate::from),
            )
        }
    }
}

@Serializable
data class AiExerciseCandidate(
    val id: String,
    val target: String,
    val equipment: String,
    @SerialName("target_type") val targetType: AiTargetType,
) {
    companion object {
        fun from(candidate: ExerciseCandidate) = AiExerciseCandidate(
            id = candidate.id.value,
            target = candidate.target.canonical.slug,
            equipment = candidate.equipment.slug,
            targetType = if (candidate.isTimed) AiTargetType.DURATION else AiTargetType.REPETITIONS,
        )
    }
}

@Serializable
enum class AiTargetType {
    @SerialName("repetitions")
    REPETITIONS,

    @SerialName("duration")
    DURATION,
}

/** The provider response before it is trusted or shown. */
@Serializable
data class AiWorkoutResponse(
    @SerialName("schema_version") val schemaVersion: Int,
    val exercises: List<AiPlannedExercise>,
    val rationale: String,
)

@Serializable
data class AiPlannedExercise(
    @SerialName("exercise_id") val exerciseId: String,
    val order: Int,
    val sets: Int,
    val repetitions: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("rest_seconds") val restSeconds: Int,
    val tempo: String? = null,
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

    fun encode(request: AiWorkoutRequest): String = json.encodeToString(request)

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
