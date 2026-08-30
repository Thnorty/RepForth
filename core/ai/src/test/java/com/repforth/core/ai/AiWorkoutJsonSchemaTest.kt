package com.repforth.core.ai

import com.repforth.core.model.WorkoutLimits
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiWorkoutJsonSchemaTest {

    @Test
    fun `schema name and version change together`() {
        assertEquals("repforth_workout_v$AI_WORKOUT_SCHEMA_VERSION", AI_WORKOUT_SCHEMA_NAME)

        val version = rootProperties().getValue("schema_version").jsonObject
        assertEquals(AI_WORKOUT_SCHEMA_VERSION, version.int("minimum"))
        assertEquals(AI_WORKOUT_SCHEMA_VERSION, version.int("maximum"))
    }

    @Test
    fun `schema takes every numeric bound from workout limits`() {
        val exercises = rootProperties().getValue("exercises").jsonObject
        assertEquals(1, exercises.int("minItems"))
        assertEquals(WorkoutLimits.maxExercises, exercises.int("maxItems"))

        val fields = exercises.getValue("items").jsonObject.properties()
        assertRange(fields.getValue("sets").jsonObject, WorkoutLimits.sets)
        assertRange(fields.getValue("rest_seconds").jsonObject, WorkoutLimits.restSeconds)
        assertRange(fields.getValue("duration_seconds").nullableValue(), WorkoutLimits.durationSeconds)
        assertDoubleRange(fields.getValue("weight_kg").nullableValue(), WorkoutLimits.weightKg)

        assertRange(fields.getValue("repetitions").nullableValue(), WorkoutLimits.reps)
    }

    @Test
    fun `every object is closed and requires every declared property`() {
        val root = AiWorkoutJsonSchema.value
        assertClosed(root)

        val exercise = rootProperties()
            .getValue("exercises")
            .jsonObject
            .getValue("items")
            .jsonObject
        assertClosed(exercise)
        assertFalse(
            "Nullable means an explicit JSON null, not an omitted property",
            exercise.getValue("required").jsonArray.isEmpty(),
        )
    }

    private fun assertClosed(schema: JsonObject) {
        assertFalse(schema.getValue("additionalProperties").jsonPrimitive.boolean)
        assertEquals(
            schema.properties().keys,
            schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet(),
        )
    }

    private fun assertRange(schema: JsonObject, range: IntRange) {
        assertEquals(range.first, schema.int("minimum"))
        assertEquals(range.last, schema.int("maximum"))
    }

    private fun assertDoubleRange(schema: JsonObject, range: ClosedFloatingPointRange<Double>) {
        assertEquals(range.start, schema.double("minimum"), 0.001)
        assertEquals(range.endInclusive, schema.double("maximum"), 0.001)
    }

    private fun rootProperties() = AiWorkoutJsonSchema.value.properties()

    private fun JsonObject.properties() = getValue("properties").jsonObject

    private fun kotlinx.serialization.json.JsonElement.nullableValue() =
        jsonObject.getValue("anyOf").jsonArray.first().jsonObject

    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.content.toInt()

    private fun JsonObject.double(name: String) = getValue(name).jsonPrimitive.content.toDouble()
}
