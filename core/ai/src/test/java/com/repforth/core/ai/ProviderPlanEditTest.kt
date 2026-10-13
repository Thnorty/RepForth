package com.repforth.core.ai

import com.repforth.core.ai.http.ProviderHttp
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

/**
 * A plan edit through both adapters, against a local server.
 *
 * The transport is the one generation uses; what is asserted here is that the
 * edit's own prompt, schema, schema name and deadline reach the wire, and that
 * its answer is decoded by the strict edit codec rather than the workout one.
 */
class ProviderPlanEditTest {

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
    fun `gemini sends the edit schema and prompt through its native envelope`() = runTest {
        server.enqueue(ok(geminiEnvelope(validEditJson)))

        val result = gemini.editPlan(configFor(ProviderId.GEMINI, "gemini-3.5-flash"), editRequest())

        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/gemini-3.5-flash:generateContent", recorded.url.encodedPath)
        val body = json.parseToJsonElement(requireNotNull(recorded.body).utf8()).jsonObject
        assertEquals(
            AiPlanEditJsonSchema.value,
            body.getValue("generationConfig").jsonObject.getValue("responseJsonSchema"),
        )
        assertTrue(geminiPrompt(body).contains("CURRENT PLAN"))
        assertEquals(PlanEditOp.REPLACE, success(result).changes.single().op)
    }

    @Test
    fun `openai compatible edit uses strict structured output under the edit schema name`() = runTest {
        server.enqueue(ok(openAiEnvelope(validEditJson)))

        val result = openAi.editPlan(configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"), editRequest())

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.url.encodedPath)
        val body = json.parseToJsonElement(requireNotNull(recorded.body).utf8()).jsonObject
        val schema = body.getValue("response_format").jsonObject.getValue("json_schema").jsonObject
        assertEquals(AI_PLAN_EDIT_SCHEMA_NAME, schema.getValue("name").jsonPrimitive.content)
        assertTrue(schema.getValue("strict").jsonPrimitive.boolean)
        assertEquals(AiPlanEditJsonSchema.value, schema.getValue("schema"))
        assertEquals("0289", success(result).changes.single().exerciseId)
    }

    /**
     * Generation scales its deadline by the number of days, because its answer
     * grows with them. An edit's answer does not, so a seven-day plan gets the
     * configured deadline and no more. Nothing is enqueued: the server accepts
     * and never answers.
     */
    @Test
    fun `the edit uses the configured timeout, not one scaled by days`() = runTest {
        val startedAt = System.nanoTime()

        val result = gemini.editPlan(
            configFor(ProviderId.GEMINI, "gemini-3.5-flash", timeoutSeconds = 1),
            editRequest(days = 7),
        )

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertEquals(ProviderFailure.TIMEOUT, (result as ProviderPlanEditResult.Failed).failure)
        assertTrue("A 1s edit deadline took ${elapsedMs}ms; was it scaled by days?", elapsedMs < 2_500)
    }

    @Test
    fun `an unknown field in the edit answer is a format failure`() = runTest {
        server.enqueue(ok(openAiEnvelope(validEditJson.dropLast(1) + ",\"surprise\":true}")))

        val result = openAi.editPlan(configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"), editRequest())
            as ProviderPlanEditResult.Failed

        assertEquals(ProviderFailure.FORMAT, result.failure)
        assertFalse(result.detail!!.contains("surprise"))
    }

    @Test
    fun `a workout answer is not an edit answer`() = runTest {
        server.enqueue(ok(geminiEnvelope("""{"days":[],"rationale":"r"}""")))

        val result = gemini.editPlan(configFor(ProviderId.GEMINI, "gemini-3.5-flash"), editRequest())

        assertEquals(ProviderFailure.FORMAT, (result as ProviderPlanEditResult.Failed).failure)
    }

    @Test
    fun `an openai refusal is a format failure without its text`() = runTest {
        server.enqueue(
            ok("""{"choices":[{"message":{"content":null,"refusal":"private refusal text"}}]}"""),
        )

        val result = openAi.editPlan(configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"), editRequest())
            as ProviderPlanEditResult.Failed

        assertEquals(ProviderFailure.FORMAT, result.failure)
        assertFalse(result.detail!!.contains("private refusal text"))
    }

    @Test
    fun `edit status failures keep the actionable categories`() = runTest {
        val cases = listOf(401 to ProviderFailure.AUTHENTICATION, 429 to ProviderFailure.QUOTA, 503 to ProviderFailure.SERVER)
        cases.forEach { (code, expected) ->
            server.enqueue(MockResponse.Builder().code(code).body("body $code").build())
            val result = openAi.editPlan(configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"), editRequest())
                as ProviderPlanEditResult.Failed
            assertEquals("HTTP $code", expected, result.failure)
            assertEquals("body $code", result.detail)
        }

        server.enqueue(
            MockResponse.Builder().code(400)
                .body("""{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}""").build(),
        )
        val gem = gemini.editPlan(configFor(ProviderId.GEMINI, "gemini-3.5-flash"), editRequest())
            as ProviderPlanEditResult.Failed
        assertEquals(ProviderFailure.AUTHENTICATION, gem.failure)
    }

    @Test
    fun `the fake records edits separately from generation`() = runTest {
        val expected = ProviderPlanEditResult.Ok(AiPlanEditResponse(emptyList(), "No change."))
        val fake = FakeAiProvider(nextEdit = expected)
        val config = configFor(ProviderId.GEMINI, "gemini-3.5-flash")
        val request = editRequest()

        assertEquals(expected, fake.editPlan(config, request))
        assertEquals(listOf(config to request), fake.editCalls)
        assertEquals(listOf<AiWorkoutRetryFeedback?>(null), fake.editRetryFeedback)
        assertTrue(fake.workoutCalls.isEmpty())
    }

    private fun editRequest(days: Int = 1) = AiPlanEditRequest(
        locale = "en",
        goal = "hypertrophy",
        experience = "beginner",
        sessionDurationMinutes = 40,
        instruction = "swap this for something similar",
        focus = PlanRef(1, 1),
        dayTitles = List(days) { "Day ${it + 1}" },
        rows = List(days) { day ->
            AiPlanRow(PlanRef(day + 1, 1), "0025", "barbell bench press", "pectorals", 4, 8, null, 60.0, 90)
        },
        candidates = listOf(
            AiExerciseCandidate("0289", "dumbbell bench press", "pectorals", listOf("triceps"), "dumbbell", false),
        ),
    )

    private fun configFor(
        provider: ProviderId,
        model: String,
        timeoutSeconds: Int = ProviderSettings.Default.requestTimeoutSeconds,
    ) = ProviderConfig(
        settings = ProviderSettings.Default.copy(
            provider = provider,
            model = model,
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            requestTimeoutSeconds = timeoutSeconds,
        ),
        apiKey = "test-not-a-real-key",
    )

    private fun success(result: ProviderPlanEditResult): AiPlanEditResponse =
        (result as ProviderPlanEditResult.Ok).response

    private fun geminiPrompt(body: JsonObject): String = body
        .getValue("contents").jsonArray.single().jsonObject
        .getValue("parts").jsonArray.single().jsonObject
        .getValue("text").jsonPrimitive.content

    private fun geminiEnvelope(answer: String) =
        """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(answer)}}]}}]}"""

    private fun openAiEnvelope(answer: String) =
        """{"choices":[{"message":{"content":${JsonPrimitive(answer)},"refusal":null}}]}"""

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    private companion object {
        const val validEditJson =
            """{"changes":[{"op":"replace","day":1,"position":1,"exercise_id":"0289","sets":4,""" +
                """"repetitions":10,"duration_seconds":null,"weight_kg":20,"rest_seconds":90}],""" +
                """"rationale":"Same muscles, dumbbells instead."}"""
    }
}
