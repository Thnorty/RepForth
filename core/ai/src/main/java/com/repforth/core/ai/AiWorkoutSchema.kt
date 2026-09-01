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
 * **Shape only — no bounds.** No `minimum`, `maximum`, `minItems` or
 * `maxItems`, and the omission is load-bearing rather than an oversight.
 *
 * Gemini rejected the bounded version of this schema outright with
 * `400 INVALID_ARGUMENT: Request contains an invalid argument`, naming nothing.
 * It was measured rather than guessed: `tools/probe-gemini-schema.ps1` and
 * `tools/probe-gemini-fields.ps1` bisect a schema against the live endpoint,
 * and the deciding run showed the full schema failing 3/3 while the identical
 * schema with every bound stripped passed 3/3. Dropping *fields* did not help;
 * dropping bounds did. Four earlier explanations — an unsupported keyword,
 * nullable-at-depth, a per-object property budget, and a flaky endpoint — were
 * each contradicted by the next measurement, so this comment records only what
 * was reproduced.
 *
 * Nothing is lost by it. [AiWorkoutValidator] already enforces every one of
 * these ranges and is authoritative in any case, because a compatible server is
 * free to ignore the schema entirely — and the ranges are now stated in the
 * prompt, so the model is still told what they are. The values continue to come
 * from [WorkoutLimits], written once.
 *
 * Re-run the probes before adding a keyword back. No JVM test can catch this:
 * MockWebServer accepts any schema at all.
 */
object AiWorkoutJsonSchema {
    val value: JsonObject = objectSchema(
        properties = linkedMapOf(
            "schema_version" to integerSchema(),
            "days" to arraySchema(daySchema()),
            "rationale" to stringSchema(),
        ),
    )

    private fun daySchema() = objectSchema(
        properties = linkedMapOf(
            "day_index" to integerSchema(),
            "title" to stringSchema(),
            "focus_muscles" to arraySchema(stringSchema()),
            "exercises" to arraySchema(exerciseSchema()),
        ),
    )

    private fun exerciseSchema() = objectSchema(
        properties = linkedMapOf(
            "exercise_id" to stringSchema(),
            "order" to integerSchema(),
            "sets" to integerSchema(),
            "repetitions" to nullable("integer"),
            "duration_seconds" to nullable("integer"),
            "weight_kg" to nullable("number"),
            "rest_seconds" to integerSchema(),
            "tempo" to nullable("string"),
        ),
    )

    private fun stringSchema() = buildJsonObject { put("type", "string") }

    private fun integerSchema() = buildJsonObject { put("type", "integer") }

    private fun arraySchema(items: JsonObject) = buildJsonObject {
        put("type", "array")
        put("items", items)
    }

    private fun objectSchema(properties: LinkedHashMap<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("properties", JsonObject(properties))
        put("required", JsonArray(properties.keys.map(::JsonPrimitive)))
    }

    /** A value that may be absent, as a type array — the form both providers accept. */
    private fun nullable(type: String) = buildJsonObject {
        put(
            "type",
            buildJsonArray {
                add(JsonPrimitive(type))
                add(JsonPrimitive("null"))
            },
        )
    }
}

/** One prompt shape, regardless of which provider carries it. */
internal fun AiWorkoutRequest.toGenerationPrompt(
    retryFeedback: AiWorkoutRetryFeedback? = null,
): String = """
    Arrange a comprehensive, well-structured multi-day training week from the typed request below.
    Generate exactly $days day(s) in the days array (with day_index from 0 to ${days - 1}).
    For each day:
    - Structure the day logically:
      - Start with 1-2 appropriate warm-up / dynamic mobility / activation movements for that day's target muscle groups.
      - Follow with the main resistance / working exercises matching the training goal, session duration ($sessionDurationMinutes min), and constraints.
      - End with 1-2 stretching / mobility cool-down exercises for the worked muscles.
    - Use only exercise IDs in candidate_exercises and obey every constraint.
    - Choose one exact integer in repetitions for each repetition-based exercise; never return a range.
    - Set order to the exercise's zero-based position within its day.
    Stay inside these limits. They are checked on arrival and an answer outside them is rejected: at most ${WorkoutLimits.maxExercisesPerDay} exercises per day; sets ${WorkoutLimits.sets.first}-${WorkoutLimits.sets.last}; repetitions ${WorkoutLimits.reps.first}-${WorkoutLimits.reps.last}; duration_seconds ${WorkoutLimits.durationSeconds.first}-${WorkoutLimits.durationSeconds.last}; rest_seconds ${WorkoutLimits.restSeconds.first}-${WorkoutLimits.restSeconds.last}; weight_kg ${WorkoutLimits.weightKg.start}-${WorkoutLimits.weightKg.endInclusive}.
    - For weighted exercises (e.g. barbell, dumbbell, cable, machine), specify an appropriate starting weight in weight_kg based on the user's experience ($experience) and goal ($goal). For bodyweight or non-weighted movements, set weight_kg to null.
    - An exercise may repeat across different days, but must not repeat within the same day.
    Return only JSON matching the supplied schema.
    Write rationale and day titles in the request locale: $locale.
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
                    issue.dayIndex?.let { put("day_index", it) }
                    issue.exerciseId?.let { put("exercise_id", it) }
                },
            )
        }
    }
    return "The previous answer failed validation. Correct these errors: $encoded"
}
