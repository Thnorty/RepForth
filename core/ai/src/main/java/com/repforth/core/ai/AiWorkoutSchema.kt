package com.repforth.core.ai

import com.repforth.core.model.WorkoutLimits
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The provider-visible name changes when the contract changes. */
const val AI_WORKOUT_SCHEMA_NAME = "repforth_workout_v$AI_WORKOUT_SCHEMA_VERSION"

/**
 * The one JSON Schema supplied to every structured-output provider (§8).
 *
 * It is deliberately built from [WorkoutLimits] rather than copied literals.
 * Provider structured output is a first defence only; [AiWorkoutValidator]
 * remains authoritative because a compatible server can ignore this schema.
 */
object AiWorkoutJsonSchema {
    val value: JsonObject = objectSchema(
        properties = linkedMapOf(
            "schema_version" to integerSchema(
                minimum = AI_WORKOUT_SCHEMA_VERSION,
                maximum = AI_WORKOUT_SCHEMA_VERSION,
            ),
            "exercises" to buildJsonObject {
                put("type", "array")
                put("minItems", 1)
                put("maxItems", WorkoutLimits.maxExercises)
                put("items", exerciseSchema())
            },
            "rationale" to buildJsonObject {
                put("type", "string")
                put("minLength", 1)
            },
        ),
    )

    private fun exerciseSchema() = objectSchema(
        properties = linkedMapOf(
            "exercise_id" to buildJsonObject {
                put("type", "string")
                put("minLength", 1)
            },
            "order" to integerSchema(0, WorkoutLimits.maxExercises - 1),
            "sets" to integerSchema(WorkoutLimits.sets.first, WorkoutLimits.sets.last),
            "repetitions" to nullable(
                integerSchema(WorkoutLimits.reps.first, WorkoutLimits.reps.last),
            ),
            "duration_seconds" to nullable(
                integerSchema(
                    WorkoutLimits.durationSeconds.first,
                    WorkoutLimits.durationSeconds.last,
                ),
            ),
            "rest_seconds" to integerSchema(
                WorkoutLimits.restSeconds.first,
                WorkoutLimits.restSeconds.last,
            ),
            "tempo" to nullable(buildJsonObject { put("type", "string") }),
        ),
    )

    private fun integerSchema(minimum: Int, maximum: Int) = buildJsonObject {
        put("type", "integer")
        put("minimum", minimum)
        put("maximum", maximum)
    }

    private fun objectSchema(properties: LinkedHashMap<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("properties", JsonObject(properties))
        put("required", JsonArray(properties.keys.map(::JsonPrimitive)))
    }

    private fun nullable(schema: JsonObject) = buildJsonObject {
        put(
            "anyOf",
            buildJsonArray {
                add(schema)
                add(buildJsonObject { put("type", "null") })
            },
        )
    }
}

/** One prompt shape, regardless of which provider carries it. */
internal fun AiWorkoutRequest.toGenerationPrompt(
    retryFeedback: AiWorkoutRetryFeedback? = null,
): String = """
    Arrange a workout from the typed request below.
    Use only exercise IDs in candidate_exercises and obey every constraint.
    Choose one exact integer in repetitions for each repetition-based exercise; never return a range.
    Return only JSON matching the supplied schema.
    Write rationale in the request locale: $locale.
    ${retryFeedback?.toPromptLine().orEmpty()}

    ${AiWorkoutCodec.encode(this)}
""".trimIndent()

private fun AiWorkoutRetryFeedback.toPromptLine(): String {
    val encoded = buildJsonArray {
        issues.forEach { issue ->
            add(
                buildJsonObject {
                    put("kind", issue.kind.name.lowercase())
                    put("code", issue.code)
                    issue.exerciseId?.let { put("exercise_id", it) }
                },
            )
        }
    }
    return "The previous answer failed validation. Correct these errors: $encoded"
}
