package com.repforth.core.ai

import com.repforth.core.model.WorkoutLimits
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plan-edit contract: what a model may answer, and the schema it is told.
 *
 * Strict for the same reason the workout codec is: an unknown field, a missing
 * one or an operation this app does not define is evidence the model did not
 * follow the contract, and the repair attempt exists for exactly that.
 */
class AiPlanEditContractTest {

    private fun change(
        op: String,
        exerciseId: String? = null,
        sets: Int? = null,
        repetitions: Int? = null,
        duration: Int? = null,
        weight: String = "null",
        rest: Int? = null,
        extra: String = "",
    ) = """
        {"op":"$op","day":1,"position":2,
         "exercise_id":${exerciseId?.let { "\"$it\"" } ?: "null"},
         "sets":$sets,"repetitions":$repetitions,"duration_seconds":$duration,
         "weight_kg":$weight,"rest_seconds":$rest$extra}
    """.trimIndent()

    private fun answer(vararg changes: String) =
        """{"changes":[${changes.joinToString(",")}],"rationale":"Because."}"""

    @Test
    fun `a complete change list decodes`() {
        val decoded = AiPlanEditCodec.decodeResponse(
            answer(
                change("replace", "0289", sets = 4, repetitions = 10, weight = "20", rest = 90),
                change("add", "0300", sets = 3, duration = 30, rest = 45),
                change("remove"),
                change("adjust", sets = 5),
            ),
        )

        val response = (decoded as AiPlanEditDecodeResult.Ok).response
        assertEquals(
            listOf(PlanEditOp.REPLACE, PlanEditOp.ADD, PlanEditOp.REMOVE, PlanEditOp.ADJUST),
            response.changes.map { it.op },
        )
        assertEquals(
            AiPlanChange(
                op = PlanEditOp.REPLACE,
                day = 1,
                position = 2,
                exerciseId = "0289",
                sets = 4,
                repetitions = 10,
                durationSeconds = null,
                weightKg = 20.0,
                restSeconds = 90,
            ),
            response.changes.first(),
        )
        assertEquals("Because.", response.rationale)
    }

    @Test
    fun `an unknown op is malformed`() {
        assertEquals(
            AiPlanEditDecodeResult.Malformed,
            AiPlanEditCodec.decodeResponse(answer(change("move"))),
        )
    }

    @Test
    fun `an unknown field is malformed`() {
        assertEquals(
            AiPlanEditDecodeResult.Malformed,
            AiPlanEditCodec.decodeResponse(answer(change("remove", extra = ",\"tempo\":\"3-1-1\""))),
        )
    }

    @Test
    fun `a missing nullable field is malformed`() {
        val withoutWeight = """
            {"changes":[{"op":"remove","day":1,"position":1,"exercise_id":null,
             "sets":null,"repetitions":null,"duration_seconds":null,"rest_seconds":null}],
             "rationale":"r"}
        """.trimIndent()

        assertEquals(AiPlanEditDecodeResult.Malformed, AiPlanEditCodec.decodeResponse(withoutWeight))
    }

    @Test
    fun `weights and durations land on the grid`() {
        val decoded = AiPlanEditCodec.decodeResponse(
            answer(
                change("replace", "0289", sets = 3, repetitions = 8, weight = "62.5", rest = 60),
                change("add", "0300", sets = 3, duration = 47, rest = 30),
            ),
        ) as AiPlanEditDecodeResult.Ok

        assertEquals(WorkoutLimits.roundWeightKg(62.5), decoded.response.changes[0].weightKg)
        assertEquals(WorkoutLimits.roundDurationSeconds(47), decoded.response.changes[1].durationSeconds)
    }

    @Test
    fun `edit schema is closed, requires every property and carries no bounds`() {
        val root = AiPlanEditJsonSchema.value
        assertClosed(root)
        assertClosed(changeSchema())

        val encoded = root.toString()
        listOf("minimum", "maximum", "minItems", "maxItems", "enum", "minLength").forEach {
            assertFalse("The edit schema sends $it: $encoded", encoded.contains("\"$it\""))
        }
    }

    @Test
    fun `op is a plain string in the schema`() {
        assertEquals(
            buildJsonObject { put("type", "string") },
            changeSchema().properties().getValue("op"),
        )
    }

    @Test
    fun `nullable fields use a type array`() {
        val expected = mapOf(
            "exercise_id" to "string",
            "sets" to "integer",
            "repetitions" to "integer",
            "duration_seconds" to "integer",
            "weight_kg" to "number",
            "rest_seconds" to "integer",
        )
        expected.forEach { (field, type) ->
            val types = changeSchema().properties().getValue(field).jsonObject
                .getValue("type").jsonArray.map { it.jsonPrimitive.content }
            assertEquals("$field type", listOf(type, "null"), types)
        }
    }

    @Test
    fun `schema name and version change together`() {
        assertTrue(AI_PLAN_EDIT_SCHEMA_NAME.endsWith("_v$AI_PLAN_EDIT_SCHEMA_VERSION"))
    }

    private fun changeSchema(): JsonObject = AiPlanEditJsonSchema.value.properties()
        .getValue("changes").jsonObject
        .getValue("items").jsonObject

    private fun assertClosed(schema: JsonObject) {
        assertFalse(schema.getValue("additionalProperties").jsonPrimitive.boolean)
        assertEquals(
            schema.properties().keys,
            schema.getValue("required").jsonArray.map(JsonElement::jsonPrimitive).map { it.content }.toSet(),
        )
    }

    private fun JsonObject.properties() = getValue("properties").jsonObject
}
