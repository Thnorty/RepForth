package com.repforth.core.ai

import com.repforth.core.ai.http.ProviderHttp
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two provider adapters, against a real HTTP server (§8).
 *
 * A local [MockWebServer] rather than a mocked OkHttp. What is worth asserting
 * here is what goes on the wire — which header carries the key, which path is
 * requested — and what a real 401 does on the way back. A mocked client asserts
 * that the adapter calls the mock.
 *
 * These run on the JVM with no device, which matters: this is the whole of the
 * networking layer, and it would otherwise be the second thing in this project
 * that could only be verified by holding a phone.
 */
class ProviderAdapterTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val http = ProviderHttp(OkHttpClient())

    private lateinit var gemini: GeminiProvider
    private val openAi = OpenAiCompatibleProvider(http, json)

    @Before
    fun setUp() {
        server.start()
        // Gemini's endpoint is fixed in production and injected here. Without
        // that, this class tests Google: the first version of these tests sent
        // a fake key to generativelanguage.googleapis.com on every run and
        // asserted against whatever it answered, which was a 400.
        gemini = GeminiProvider(http, json, server.url("/v1beta/").toString())
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * The server is on `127.0.0.1`, so every test here needs the cleartext
     * setting on — which also means the loopback path is exercised rather than
     * described.
     */
    private fun configFor(provider: ProviderId, model: String) = ProviderConfig(
        settings = ProviderSettings.Default.copy(
            provider = provider,
            model = model,
            baseUrl = server.url("/v1/").toString(),
            allowCleartext = true,
        ),
        apiKey = "test-not-a-real-key",
    )

    @Test
    fun `gemini sends the key in its own header`() = runTest {
        server.enqueue(ok("""{"models":[{"name":"models/gemini-3.5-flash"}]}"""))

        gemini.testConnection(configFor(ProviderId.GEMINI, "gemini-3.5-flash"))

        val request = server.takeRequest()
        assertEquals("test-not-a-real-key", request.headers["x-goog-api-key"])
        assertEquals(
            "A bearer token is the other provider's convention, not this one",
            null,
            request.headers["Authorization"],
        )
    }

    @Test
    fun `gemini confirms a model that is offered`() = runTest {
        server.enqueue(
            ok("""{"models":[{"name":"models/gemini-3.5-flash"},{"name":"models/other"}]}"""),
        )

        val result = gemini.testConnection(configFor(ProviderId.GEMINI, "gemini-3.5-flash"))

        assertEquals(ProviderTestResult.Ok(modelConfirmed = true), result)
    }

    /**
     * The `models/` prefix is Gemini's, and the user types the bare id. Getting
     * this wrong reports every correctly configured model as missing.
     */
    @Test
    fun `gemini reports a model that is not offered, by name`() = runTest {
        server.enqueue(ok("""{"models":[{"name":"models/gemini-3.5-flash"}]}"""))

        val result = gemini.testConnection(configFor(ProviderId.GEMINI, "gemini-9-ultra"))

        val failed = result as ProviderTestResult.Failed
        assertEquals(ProviderFailure.MODEL_NOT_FOUND, failed.failure)
        assertTrue("${failed.detail}", failed.detail!!.contains("gemini-9-ultra"))
    }

    /**
     * Gemini ignores the address field entirely, and that is a safety property
     * rather than a tidiness one.
     *
     * If the adapter honoured a stored base URL, anything that could write that
     * preference — a bad import, a future settings bug — could point the app at
     * a server of its choosing and be handed the user's Gemini key on the next
     * request. The endpoint is fixed in the type *and* in the adapter, so it
     * takes two mistakes rather than one.
     */
    @Test
    fun `gemini does not send to an address stored in the settings`() = runTest {
        server.enqueue(ok("""{"models":[{"name":"models/gemini-3.5-flash"}]}"""))
        val elsewhere = ProviderConfig(
            settings = ProviderSettings.Default.copy(
                provider = ProviderId.GEMINI,
                model = "gemini-3.5-flash",
                baseUrl = "https://somewhere-that-is-not-google.example/v1/",
                allowCleartext = true,
            ),
            apiKey = "test-not-a-real-key",
        )

        gemini.testConnection(elsewhere)

        assertEquals(
            "The request must go to Gemini's own endpoint, not a stored one",
            1,
            server.requestCount,
        )
    }

    @Test
    fun `the openai adapter sends a bearer token`() = runTest {
        server.enqueue(ok("""{"data":[{"id":"llama3.1"}]}"""))

        openAi.testConnection(configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"))

        assertEquals("Bearer test-not-a-real-key", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `the openai adapter confirms a model that is offered`() = runTest {
        server.enqueue(ok("""{"data":[{"id":"llama3.1"},{"id":"mistral"}]}"""))

        val result = openAi.testConnection(configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"))

        assertEquals(ProviderTestResult.Ok(modelConfirmed = true), result)
    }

    /**
     * Plenty of compatible servers implement chat and not the model list. That
     * is "connected, cannot confirm the model" — reporting it as a failure
     * sends the user to fix a working setup.
     */
    @Test
    fun `a server with no model list is reachable, not broken`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).body("Not Found").build())

        val result = openAi.testConnection(configFor(ProviderId.OPENAI_COMPATIBLE, "llama3.1"))

        assertEquals(ProviderTestResult.Ok(modelConfirmed = false), result)
    }

    @Test
    fun `a rejected key is an authentication failure, not a network one`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"bad key"}""").build())

        val result = gemini.testConnection(configFor(ProviderId.GEMINI, "gemini-3.5-flash"))

        assertEquals(
            ProviderFailure.AUTHENTICATION,
            (result as ProviderTestResult.Failed).failure,
        )
    }

    /**
     * A rate limit is the user's account being busy, not a mistake they made
     * here. Telling them to check their key would send them to change something
     * that was correct.
     */
    @Test
    fun `a rate limit is reported as quota, not as a bad key`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).body("slow down").build())

        val result = gemini.testConnection(configFor(ProviderId.GEMINI, "gemini-3.5-flash"))

        val failed = result as ProviderTestResult.Failed
        assertEquals("detail was ${failed.detail}", ProviderFailure.QUOTA, failed.failure)
    }

    @Test
    fun `a broken provider is reported as a server failure`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).body("down").build())

        val result = gemini.testConnection(configFor(ProviderId.GEMINI, "gemini-3.5-flash"))

        assertEquals(ProviderFailure.SERVER, (result as ProviderTestResult.Failed).failure)
    }

    @Test
    fun `a reply that is not the expected shape is a format failure`() = runTest {
        server.enqueue(ok("<html>hello</html>"))

        val result = gemini.testConnection(configFor(ProviderId.GEMINI, "gemini-3.5-flash"))

        assertEquals(ProviderFailure.FORMAT, (result as ProviderTestResult.Failed).failure)
    }

    /**
     * The test this file exists for.
     *
     * The endpoint rule has to hold at the socket, not only in the settings
     * screen — a check that lives in a text field is one the next caller skips.
     * So this asserts both that the call is refused *and* that the server never
     * heard from us: a refusal reported after the key was already sent would be
     * worthless.
     *
     * Watched failing with the `EndpointPolicy` check removed from
     * `ProviderHttp.send`: the request arrived, key and all.
     */
    @Test
    fun `a cleartext address is not contacted at all when the setting is off`() = runTest {
        server.enqueue(ok("""{"data":[{"id":"llama3.1"}]}"""))

        val config = ProviderConfig(
            settings = ProviderSettings.Default.copy(
                provider = ProviderId.OPENAI_COMPATIBLE,
                model = "llama3.1",
                baseUrl = server.url("/v1/").toString(),
                allowCleartext = false,
            ),
            apiKey = "test-not-a-real-key",
        )

        val result = openAi.testConnection(config)

        // The request count is asserted first and the type before the cast, so
        // removing the endpoint check reports "the key was sent" rather than a
        // ClassCastException that names nothing.
        assertEquals(
            "The key must not reach a server this app refused to talk to",
            0,
            server.requestCount,
        )
        assertTrue(
            "Expected a refusal, got $result",
            result is ProviderTestResult.Failed,
        )
        assertEquals(
            ProviderFailure.ENDPOINT_REFUSED,
            (result as ProviderTestResult.Failed).failure,
        )
    }

    @Test
    fun `the generic provider with no address is refused before any lookup`() = runTest {
        val config = ProviderConfig(
            settings = ProviderSettings.Default.copy(
                provider = ProviderId.OPENAI_COMPATIBLE,
                baseUrl = "",
            ),
            apiKey = "test-not-a-real-key",
        )

        assertEquals(
            ProviderFailure.ENDPOINT_REFUSED,
            (openAi.testConnection(config) as ProviderTestResult.Failed).failure,
        )
    }

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
}
