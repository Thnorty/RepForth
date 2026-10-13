package com.repforth.core.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * The JSON Schema building blocks every structured-output contract is made of.
 *
 * Shared rather than copied per schema because the shapes are where provider
 * compatibility lives: the type-array nullable form and the closed object are
 * the forms Gemini and strict Chat Completions both accept, and a second copy
 * would be one edit away from sending a shape one of them rejects. See
 * [AiWorkoutJsonSchema] for why none of these carry bounds.
 */

internal fun stringSchema() = buildJsonObject { put("type", "string") }

internal fun integerSchema() = buildJsonObject { put("type", "integer") }

internal fun arraySchema(items: JsonObject) = buildJsonObject {
    put("type", "array")
    put("items", items)
}

/** Closed, and every declared property required — nullable means explicit null. */
internal fun objectSchema(properties: LinkedHashMap<String, JsonObject>) = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put("properties", JsonObject(properties))
    put("required", JsonArray(properties.keys.map(::JsonPrimitive)))
}

/** A value that may be absent, as a type array — the form both providers accept. */
internal fun nullable(type: String) = buildJsonObject {
    put(
        "type",
        buildJsonArray {
            add(JsonPrimitive(type))
            add(JsonPrimitive("null"))
        },
    )
}
