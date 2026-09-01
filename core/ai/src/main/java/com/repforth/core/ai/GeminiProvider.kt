package com.repforth.core.ai

import com.repforth.core.ai.http.ProviderHttp
import com.repforth.core.ai.http.PROVIDER_JSON_MEDIA_TYPE
import com.repforth.core.ai.http.failureForStatus
import com.repforth.core.ai.http.generationTimeoutSeconds
import com.repforth.core.ai.http.providerEndpoint
import com.repforth.core.ai.http.toProviderFailure
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Gemini, over its own REST API (§8).
 *
 * A native adapter rather than the generic one because the shapes are Gemini's:
 * the key travels in `x-goog-api-key` rather than a bearer token, and model ids
 * come back prefixed with `models/`.
 *
 * The DTOs are internal and stay that way. §8: "Keep provider-specific DTOs
 * internal and map them to shared domain requests" — the moment one of these
 * leaks upward, the rest of the app has learned a vendor's field names.
 */
internal class GeminiProvider(
    private val http: ProviderHttp,
    private val json: Json,
    /**
     * Gemini's endpoint, which the user cannot change (§8: the address field is
     * not even shown for this provider).
     *
     * A constructor parameter rather than a constant read inline, because
     * otherwise this class can only be tested against Google. The first version
     * took the URL from `ProviderConfig`, which for Gemini resolves to the fixed
     * endpoint — so the adapter tests quietly made real requests to
     * generativelanguage.googleapis.com and asserted against whatever came
     * back. Production still passes the constant; only a test passes anything
     * else.
     */
    private val baseUrl: String = ProviderSettings.GEMINI_BASE_URL,
) : AiProvider {

    override val id = ProviderId.GEMINI

    /**
     * Lists models, which answers all three questions at once.
     *
     * Cheaper and safer than a generation call: it does not consume tokens, so
     * pressing "Test connection" cannot cost the user anything, and it tells
     * reachability, authentication and model existence apart — a generation
     * request that failed would only say that something went wrong.
     */
    override suspend fun testConnection(config: ProviderConfig): ProviderTestResult {
        val request = Request.Builder()
            .url(providerEndpoint(baseUrl, "models"))
            .withApiKey(config)
            .get()
            .build()

        val reply = http.send(
            request = request,
            timeoutSeconds = config.settings.requestTimeoutSeconds,
        ).getOrElse { cause ->
            return ProviderTestResult.Failed(cause.toProviderFailure(), cause.message)
        }

        if (reply.code !in 200..299) {
            return ProviderTestResult.Failed(
                failureFor(reply.code),
                "HTTP ${reply.code}",
            )
        }

        val models = runCatching { json.decodeFromString<ModelList>(reply.body) }
            .getOrElse {
                return ProviderTestResult.Failed(ProviderFailure.FORMAT, it.message)
            }
            // `models/gemini-3.5-flash` is the name; `gemini-3.5-flash` is what
            // the user typed and what the generate endpoint takes.
            .models
            .map { it.name.removePrefix("models/") }

        if (models.isEmpty()) return ProviderTestResult.Ok(modelConfirmed = false)

        return if (config.model in models) {
            ProviderTestResult.Ok(modelConfirmed = true)
        } else {
            ProviderTestResult.Failed(
                ProviderFailure.MODEL_NOT_FOUND,
                "not offered: ${config.model}",
            )
        }
    }

    override suspend fun generateWorkout(
        config: ProviderConfig,
        request: AiWorkoutRequest,
        retryFeedback: AiWorkoutRetryFeedback?,
    ): ProviderGenerationResult {
        val wireRequest = runCatching {
            val body = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = request.toGenerationPrompt(retryFeedback)),
                        ),
                    ),
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    responseJsonSchema = AiWorkoutJsonSchema.value,
                ),
            )
            Request.Builder()
                .url(
                    providerEndpoint(
                        baseUrl,
                        "models/${config.model}:generateContent",
                    ),
                )
                .withApiKey(config)
                .post(json.encodeToString(body).toRequestBody(PROVIDER_JSON_MEDIA_TYPE))
                .build()
        }.getOrElse {
            return ProviderGenerationResult.Failed(
                ProviderFailure.FORMAT,
                "Invalid provider request configuration",
            )
        }

        val reply = http.send(
            request = wireRequest,
            timeoutSeconds = generationTimeoutSeconds(
                config.settings.requestTimeoutSeconds,
                request.days,
            ),
        ).getOrElse { cause ->
            // No detail: nothing came back. A timeout or a DNS failure has no
            // server response to show, and putting the exception's word
            // ("timeout") under a heading that says "Server response" would be
            // the app inventing a reply the server never sent.
            return ProviderGenerationResult.Failed(cause.toProviderFailure(), null)
        }

        if (reply.code !in 200..299) {
            return ProviderGenerationResult.Failed(
                failureForGeneration(reply.code, reply.body),
                providerDetail(reply.body),
            )
        }

        val envelope = runCatching {
            json.decodeFromString<GeminiGenerateResponse>(reply.body)
        }.getOrElse {
            return ProviderGenerationResult.Failed(
                ProviderFailure.FORMAT,
                "Invalid Gemini response envelope",
            )
        }
        val structured = envelope.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.firstNotNullOfOrNull(GeminiPart::text)
            ?: return ProviderGenerationResult.Failed(
                ProviderFailure.FORMAT,
                "Gemini response contained no structured workout",
            )

        return when (val decoded = AiWorkoutCodec.decodeResponse(structured)) {
            is AiWorkoutDecodeResult.Ok -> {
                ProviderGenerationResult.Ok(decoded.response)
            }
            AiWorkoutDecodeResult.Malformed -> {
                ProviderGenerationResult.Failed(
                    ProviderFailure.FORMAT,
                    "Gemini returned a malformed structured workout",
                )
            }
        }
    }

    /**
     * Gemini answers an invalid key with 400, not 401.
     *
     * Verified against the live endpoint: a bad key returns
     * `400 INVALID_ARGUMENT` with `"reason": "API_KEY_INVALID"`, which the
     * shared status mapping reads as a 4xx it does not recognise and reports as
     * "the provider answered with something this app could not read". That is
     * the single most likely thing to go wrong — a mistyped or revoked key —
     * getting the least useful of the seven messages.
     *
     * Kept local to this call rather than folded into `failureForStatus`,
     * because a 400 elsewhere means something else: a generation request has a
     * body, and a body can be wrong on its own account. The model list has no
     * body and no parameters, so a 400 here can only be about the credential.
     */
    /**
     * The response body exactly as it arrived.
     *
     * Verbatim rather than summarised: the whole point of showing this is that
     * the app's own categories were what hid the real problem, and any field
     * this code chooses to extract is another guess about which part matters.
     * Bounded only in length, and rendered as a quotation because since §8
     * stopped inspecting the address this text comes from whatever host the
     * user configured.
     */
    private fun providerDetail(body: String): String? =
        body.trim().takeIf { it.isNotEmpty() }?.take(PROVIDER_DETAIL_MAX_CHARS)

    private fun failureFor(code: Int): ProviderFailure =
        if (code == 400) ProviderFailure.AUTHENTICATION else failureForStatus(code)

    private fun Request.Builder.withApiKey(config: ProviderConfig): Request.Builder =
        header("x-goog-api-key", config.apiKey)

    private fun failureForGeneration(code: Int, body: String): ProviderFailure {
        if (code == 404) return ProviderFailure.MODEL_NOT_FOUND
        if (code != 400) return failureForStatus(code)

        val invalidKey = runCatching { json.decodeFromString<GeminiErrorEnvelope>(body) }
            .getOrNull()
            ?.error
            ?.details
            ?.any { it.reason == "API_KEY_INVALID" }
            ?: false
        return if (invalidKey) ProviderFailure.AUTHENTICATION else ProviderFailure.FORMAT
    }

    @Serializable
    private data class ModelList(
        @SerialName("models") val models: List<Model> = emptyList(),
    )

    @Serializable
    private data class Model(@SerialName("name") val name: String)

    @Serializable
    private data class GeminiGenerateRequest(
        val contents: List<GeminiContent>,
        @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig,
    )

    @Serializable
    private data class GeminiGenerationConfig(
        @SerialName("responseMimeType") val responseMimeType: String,
        @SerialName("responseJsonSchema") val responseJsonSchema: kotlinx.serialization.json.JsonObject,
    )

    @Serializable
    private data class GeminiGenerateResponse(
        val candidates: List<GeminiCandidate> = emptyList(),
    )

    @Serializable
    private data class GeminiCandidate(val content: GeminiContent? = null)

    @Serializable
    private data class GeminiContent(val parts: List<GeminiPart> = emptyList())

    @Serializable
    private data class GeminiPart(val text: String? = null)

    @Serializable
    private data class GeminiErrorEnvelope(val error: GeminiError? = null)

    /**
     * One envelope serving both readers of a Gemini error.
     *
     * [details] is what tells an invalid key from a malformed request, since
     * Gemini answers both with 400; [message] is the human sentence shown to
     * the user. They were briefly two types with the same name in this file,
     * which is the sort of duplicate that compiles right up until someone edits
     * only one of them.
     */
    @Serializable
    private data class GeminiError(
        val code: Int? = null,
        val message: String? = null,
        val status: String? = null,
        val details: List<GeminiErrorDetail> = emptyList(),
    )

    @Serializable
    private data class GeminiErrorDetail(val reason: String? = null)
}
