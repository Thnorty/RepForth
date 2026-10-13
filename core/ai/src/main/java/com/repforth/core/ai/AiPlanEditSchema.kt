package com.repforth.core.ai

import kotlinx.serialization.json.JsonObject

/**
 * The JSON Schema for a plan edit, built from the same shapes as
 * [AiWorkoutJsonSchema] and under the same rule: shape only, no bounds, no
 * `enum`. `op` is a plain string for that reason; the prompt lists its values
 * and the strict codec refuses any other.
 *
 * `tools/gemini-plan-edit-schema.json` is a dump of this, kept current by
 * `SchemaDumpGuardTest`, so the Gemini probe scripts can be pointed at it.
 */
object AiPlanEditJsonSchema {
    val value: JsonObject = objectSchema(
        properties = linkedMapOf(
            "changes" to arraySchema(changeSchema()),
            "rationale" to stringSchema(),
        ),
    )

    private fun changeSchema() = objectSchema(
        properties = linkedMapOf(
            "op" to stringSchema(),
            "day" to integerSchema(),
            "position" to integerSchema(),
            "exercise_id" to nullable("string"),
            "sets" to nullable("integer"),
            "repetitions" to nullable("integer"),
            "duration_seconds" to nullable("integer"),
            "weight_kg" to nullable("number"),
            "rest_seconds" to nullable("integer"),
        ),
    )
}
