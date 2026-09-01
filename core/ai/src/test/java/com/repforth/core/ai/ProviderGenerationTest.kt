package com.repforth.core.ai

import com.repforth.core.ai.http.ProviderHttp
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderGenerationTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val http = ProviderHttp(OkHttpClient())

    private lateinit var gemini: GeminiProvider
    private val openAi = OpenAiCompatibleProvider(http, json)

    @Before
    fun setUp() {
        server.start()
        gemini = GeminiProvider(http, json, server.url("/v1beta/").toString())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `gemini sends the shared JSON schema through its native envelope`() = runTest {
        server.enqueue(ok(geminiEnvelope(validWorkoutJson)))

        val result = gemini.generateWorkout(
            configFor(ProviderId.GEMINI, "gemini-3.5-flash"),
            workoutRequest,
        )

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(
            "/v1beta/models/gemini-3.5-flash:generateContent",
            recorded.url.encodedPath,
        )
        assertEquals("test-not-a-real-key", recorded.headers["x-goog-api-key"])

        val body = json.parseToJsonElement(requireNotNull(recorded.body).utf8()).jsonObject
        val generation = body.getValue("generationConfig").jsonObject
        assertEquals("application/json", generation.string("responseMimeType"))
        assertEquals(AiWorkoutJsonSchema.value, generation.getValue("responseJsonSchema"))
        assertTrue(geminiPrompt(body).contains(AiWorkoutCodec.encode(workoutRequest)))
        assertEquals("exercise-a", success(result).days.single().exercises.single().exerciseId)
    }

    @Test
    fun `openai compatible generation uses strict chat structured output`() = runTest {
        server.enqueue(ok(openAiEnvelope(validWorkoutJson)))
        val keyless = configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1", apiKey = "")

        val result = openAi.generateWorkout(keyless, workoutRequest)

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.url.encodedPath)
        assertEquals(null, recorded.headers["Authorization"])

        val body = json.parseToJsonElement(requireNotNull(recorded.body).utf8()).jsonObject
        val format = body.getValue("response_format").jsonObject
        val schema = format.getValue("json_schema").jsonObject
        assertEquals("json_schema", format.string("type"))
        assertEquals(AI_WORKOUT_SCHEMA_NAME, schema.string("name"))
        assertTrue(schema.getValue("strict").jsonPrimitive.boolean)
        assertEquals(AiWorkoutJsonSchema.value, schema.getValue("schema"))
        assertEquals("exercise-a", success(result).days.single().exercises.single().exerciseId)
    }

    @Test
    fun `provider envelope may evolve but the structured workout stays strict`() = runTest {
        server.enqueue(
            ok(
                """{"id":"new-envelope-field","choices":[{"message":{"role":"assistant","content":${JsonPrimitive(validWorkoutJson.dropLast(1) + ",\"surprise\":true}")}}}]}""",
            ),
        )

        val result = openAi.generateWorkout(
            configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"),
            workoutRequest,
        )

        val failed = result as ProviderGenerationResult.Failed
        assertEquals(ProviderFailure.FORMAT, failed.failure)
        assertFalse("Raw model output must not reach diagnostics", failed.detail!!.contains("surprise"))
    }

    @Test
    fun `openai refusal is a typed failure and never exposes refusal text`() = runTest {
        server.enqueue(
            ok(
                """{"choices":[{"message":{"content":null,"refusal":"private refusal text"}}]}""",
            ),
        )

        val result = openAi.generateWorkout(
            configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"),
            workoutRequest,
        ) as ProviderGenerationResult.Failed

        assertEquals(ProviderFailure.FORMAT, result.failure)
        assertFalse(result.detail!!.contains("private refusal text"))
    }

    @Test
    fun `gemini generation distinguishes its invalid key response from a bad body`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .body(
                    """{"error":{"details":[{"reason":"API_KEY_INVALID","extra":"ignored"}]}}""",
                )
                .build(),
        )

        val result = gemini.generateWorkout(
            configFor(ProviderId.GEMINI, "gemini-3.5-flash"),
            workoutRequest,
        ) as ProviderGenerationResult.Failed

        assertEquals(ProviderFailure.AUTHENTICATION, result.failure)
        // The category still comes from `details.reason`; the detail is now the
        // whole body, so the user can see the reason the app acted on.
        assertEquals(
            """{"error":{"details":[{"reason":"API_KEY_INVALID","extra":"ignored"}]}}""",
            result.detail,
        )
    }

    @Test
    fun `generation status failures keep the existing actionable categories`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).body("a quota body").build())

        val result = openAi.generateWorkout(
            configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"),
            workoutRequest,
        ) as ProviderGenerationResult.Failed

        assertEquals(ProviderFailure.QUOTA, result.failure)
        // **Reversed deliberately.** This used to assert `"HTTP 429"` against a
        // body fixture named "private quota body", and the fixture's name was
        // the point: §8 held that a provider body must never reach a diagnostic
        // result. That rule cost a real diagnosis. Gemini answered 503 with
        // "spikes in demand are usually temporary", the app showed a generic
        // failure, and finding out why took a device, a temporary logging build
        // and a round trip. The maintainer asked for the body verbatim; the
        // body is now shown verbatim, bounded and quoted.
        assertEquals("a quota body", result.detail)
    }

    @Test
    fun `generation refuses an unusable generic address without throwing`() = runTest {
        val config = ProviderConfig(
            settings = ProviderSettings.Default.copy(
                provider = ProviderId.OPENAI_COMPATIBLE,
                baseUrl = "not a URL",
            ),
            apiKey = "test-not-a-real-key",
        )

        val result = openAi.generateWorkout(config, workoutRequest)
            as ProviderGenerationResult.Failed

        assertEquals(ProviderFailure.ENDPOINT_REFUSED, result.failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `fake provider records generation independently from connection tests`() = runTest {
        val expected = ProviderGenerationResult.Ok(successResponse)
        val fake = FakeAiProvider(nextWorkout = expected)
        val config = configFor(ProviderId.GEMINI, "gemini-3.5-flash")

        val actual = fake.generateWorkout(config, workoutRequest)

        assertEquals(expected, actual)
        assertEquals(listOf(config to workoutRequest), fake.workoutCalls)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun `retry feedback is encoded as typed JSON rather than interpolated text`() {
        val prompt = workoutRequest.toGenerationPrompt(
            AiWorkoutRetryFeedback(
                listOf(
                    AiWorkoutRetryIssue(
                        kind = AiWorkoutRetryIssueKind.CONTRACT,
                        code = "exercise_not_offered",
                        dayIndex = 0,
                        exerciseId = "id-with-\"quote",
                    ),
                ),
            ),
        )

        assertTrue(prompt.contains("The previous answer failed validation"))
        assertTrue(prompt.contains("one exact integer in repetitions"))
        assertTrue(prompt.contains("\"kind\":\"contract\""))
        assertTrue(prompt.contains("\"day_index\":0"))
        assertTrue(prompt.contains("id-with-\\\"quote"))
    }

    @Test
    fun `an oversized provider body is truncated before it reaches the dialog`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("x".repeat(50_000)).build())

        val result = openAi.generateWorkout(
            configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"),
            workoutRequest,
        ) as ProviderGenerationResult.Failed

        assertEquals(
            "Since §8 stopped inspecting the address, this body comes from " +
                "whatever host the user typed. It must not be able to fill the " +
                "dialog without limit.",
            PROVIDER_DETAIL_MAX_CHARS,
            result.detail?.length,
        )
    }

    @Test
    fun `a timeout carries no server response, because none arrived`() = runTest {
        // Nothing enqueued: the server accepts and never answers.
        val result = gemini.generateWorkout(
            configFor(ProviderId.GEMINI, "gemini-3.5-flash", timeoutSeconds = 1),
            workoutRequest,
        ) as ProviderGenerationResult.Failed

        assertEquals(ProviderFailure.TIMEOUT, result.failure)
        assertEquals(
            "A timeout has no body to show; labelling the exception's word " +
                "\"timeout\" as a server response would invent a reply.",
            null,
            result.detail,
        )
    }

    private fun configFor(
        provider: ProviderId,
        model: String,
        apiKey: String = "test-not-a-real-key",
        timeoutSeconds: Int = ProviderSettings.Default.requestTimeoutSeconds,
    ) = ProviderConfig(
        settings = ProviderSettings.Default.copy(
            provider = provider,
            model = model,
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            requestTimeoutSeconds = timeoutSeconds,
        ),
        apiKey = apiKey,
    )

    private fun success(result: ProviderGenerationResult): AiWorkoutResponse =
        (result as ProviderGenerationResult.Ok).response

    private fun geminiPrompt(body: kotlinx.serialization.json.JsonObject): String = body
        .getValue("contents")
        .jsonArray
        .single()
        .jsonObject
        .getValue("parts")
        .jsonArray
        .single()
        .jsonObject
        .string("text")

    private fun geminiEnvelope(workout: String) =
        """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(workout)}}]},"new_field":true}]}"""

    private fun openAiEnvelope(workout: String) =
        """{"choices":[{"message":{"content":${JsonPrimitive(workout)},"refusal":null}}],"usage":{}}"""

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    private fun kotlinx.serialization.json.JsonObject.string(name: String) =
        getValue(name).jsonPrimitive.content

    private companion object {
        val workoutRequest = AiWorkoutRequest(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            locale = "en",
            goal = "hypertrophy",
            experience = "beginner",
            days = 1,
            sessionDurationMinutes = 40,
            maxExercisesPerDay = com.repforth.core.model.WorkoutLimits.maxExercisesPerDay,
            primaryMuscles = listOf("pectorals"),
            secondaryMuscles = listOf("triceps"),
            excludedMuscles = emptyList(),
            excludedExerciseIds = emptyList(),
            excludedMovements = emptyList(),
            equipment = listOf("dumbbell"),
            candidateExercises = listOf(
                AiExerciseCandidate(
                    id = "exercise-a",
                    target = "pectorals",
                    equipment = "dumbbell",
                    targetType = AiTargetType.REPETITIONS,
                ),
            ),
        )

        val successResponse = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(
                AiPlannedDay(
                    dayIndex = 0,
                    title = "Push",
                    exercises = listOf(
                        AiPlannedExercise(
                            exerciseId = "exercise-a",
                            order = 0,
                            sets = 3,
                            repetitions = 10,
                            restSeconds = 60,
                        ),
                    ),
                ),
            ),
            rationale = "Balanced volume",
        )

        const val validWorkoutJson =
            """{"schema_version":3,"days":[{"day_index":0,"title":"Push","focus_muscles":[],"exercises":[{"exercise_id":"exercise-a","order":0,"sets":3,"repetitions":10,"duration_seconds":null,"weight_kg":null,"rest_seconds":60,"tempo":null}]}],"rationale":"Balanced volume"}"""
    }
}
