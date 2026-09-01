package com.repforth.core.ai

import com.repforth.core.model.WorkoutLimits
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkoutJsonSchemaTest {

    private fun requiredAtRoot(): List<String> =
        AiWorkoutJsonSchema.value.getValue("required").jsonArray.map { it.jsonPrimitive.content }

    private fun sampleRequest() = AiWorkoutRequest(
        schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
        locale = "en",
        goal = "strength",
        experience = "intermediate",
        days = 3,
        sessionDurationMinutes = 45,
        maxExercisesPerDay = WorkoutLimits.maxExercisesPerDay,
        primaryMuscles = listOf("pectorals"),
        secondaryMuscles = emptyList(),
        excludedMuscles = emptyList(),
        excludedExerciseIds = emptyList(),
        excludedMovements = emptyList(),
        equipment = listOf("barbell"),
        candidateExercises = emptyList(),
    )

    @Test
    fun `schema name and version change together`() {
        assertEquals("repforth_workout_v$AI_WORKOUT_SCHEMA_VERSION", AI_WORKOUT_SCHEMA_NAME)

        // The version was pinned with minimum/maximum until Gemini rejected
        // every bound in this schema. It is pinned by AiWorkoutValidator now,
        // which refuses a response whose schema_version is not this one — see
        // `AiWorkoutValidatorTest`. All the schema still says is that the field
        // exists and is an integer.
        val version = rootProperties().getValue("schema_version").jsonObject
        assertEquals("integer", version.getValue("type").jsonPrimitive.content)
        assertTrue("schema_version must be required", "schema_version" in requiredAtRoot())
    }

    /**
     * The schema carries shape and nothing else.
     *
     * Watched failing by putting `minimum` back on `sets`.
     *
     * Gemini rejects the bounded form of this schema with
     * `400 INVALID_ARGUMENT` and names nothing; see the note on
     * [AiWorkoutJsonSchema]. This guard exists because no other test in this
     * repository can catch a schema Google refuses — MockWebServer accepts
     * anything — so the only defence against someone reasonably re-adding a
     * bound is a test that says not to, and why.
     */
    @Test
    fun `the schema carries no bounds, because Gemini rejects them`() {
        val encoded = AiWorkoutJsonSchema.value.toString()

        listOf("minimum", "maximum", "minItems", "maxItems", "minLength").forEach { keyword ->
            assertFalse(
                "The schema sends `$keyword`, which Gemini rejected outright. " +
                    "Re-run tools/probe-gemini-fields.ps1 before restoring it; " +
                    "AiWorkoutValidator enforces the range regardless.",
                encoded.contains(keyword),
            )
        }
    }

    /**
     * What the schema stopped saying, the prompt now says.
     *
     * Removing the bounds left the model with no numeric guidance at all, which
     * would trade a rejected request for a rejected answer. The limits still
     * come from [WorkoutLimits], so they are written once whichever way they
     * travel.
     */
    @Test
    fun `the prompt states the limits the schema no longer carries`() {
        val prompt = sampleRequest().toGenerationPrompt()

        assertTrue(
            "The prompt must state the sets range",
            prompt.contains("sets ${WorkoutLimits.sets.first}-${WorkoutLimits.sets.last}"),
        )
        assertTrue(
            "The prompt must state the repetitions range",
            prompt.contains("repetitions ${WorkoutLimits.reps.first}-${WorkoutLimits.reps.last}"),
        )
        assertTrue(
            "The prompt must state the rest range",
            prompt.contains(
                "rest_seconds ${WorkoutLimits.restSeconds.first}-" +
                    "${WorkoutLimits.restSeconds.last}",
            ),
        )
    }

    @Test
    fun `every object is closed and requires every declared property`() {
        val root = AiWorkoutJsonSchema.value
        assertClosed(root)

        val day = rootProperties().getValue("days").jsonObject.getValue("items").jsonObject
        assertClosed(day)

        val exercise = day.properties().getValue("exercises").jsonObject.getValue("items").jsonObject
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



    private fun rootProperties() = AiWorkoutJsonSchema.value.properties()

    private fun JsonObject.properties() = getValue("properties").jsonObject

    /**
     * A nullable field carries its constraints alongside a type array.
     *
     * Was `anyOf[0]`. Gemini rejected that shape live with
     * `400 INVALID_ARGUMENT`; its subset permits null only "by including null
     * in the type array", so the bounds now sit on the field itself.
     */
    /**
     * The schema stays inside the subset Gemini documents.
     *
     * Watched failing by restoring the `anyOf` nullable form.
     *
     * This is a guard against a whole class of failure that is invisible on the
     * JVM: every test here answers from MockWebServer, which will accept any
     * schema at all, so nothing else in this repository notices that a schema
     * Google rejects is being sent. The one live symptom was
     * `400 INVALID_ARGUMENT: Request contains an invalid argument`, with no
     * indication of which argument.
     */
    @Test
    fun `nullable fields use a type array rather than an anyOf null branch`() {
        val exercise = AiWorkoutJsonSchema.value
            .getValue("properties").jsonObject
            .getValue("days").jsonObject
            .getValue("items").jsonObject
            .getValue("properties").jsonObject
            .getValue("exercises").jsonObject
            .getValue("items").jsonObject
            .getValue("properties").jsonObject

        listOf("repetitions", "duration_seconds", "weight_kg", "tempo").forEach { field ->
            val schema = exercise.getValue(field).jsonObject
            assertNull(
                "$field must not use anyOf; Gemini rejects that shape",
                schema["anyOf"],
            )
            val types = schema.getValue("type").jsonArray.map { it.jsonPrimitive.content }
            assertTrue(
                "$field must permit null through its type array, got $types",
                "null" in types,
            )
        }
    }

    /**
     * `minLength` is not one of the string constraints Gemini honours, and a
     * schema it does not understand is a schema it can reject outright. Blank
     * strings are refused by AiWorkoutValidator, which is authoritative anyway.
     */
    @Test
    fun `no string constraint outside the documented subset is sent`() {
        val encoded = AiWorkoutJsonSchema.value.toString()
        assertFalse(
            "The schema still sends minLength: $encoded",
            encoded.contains("minLength"),
        )
    }


    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.content.toInt()

    private fun JsonObject.double(name: String) = getValue(name).jsonPrimitive.content.toDouble()
}
