package com.repforth.core.ai

import com.repforth.core.ai.http.ProviderHttp
import com.repforth.core.ai.http.failureForStatus
import com.repforth.core.ai.http.toProviderFailure
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

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
            .url(baseUrl + "models")
            .header("x-goog-api-key", config.apiKey)
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
    private fun failureFor(code: Int): ProviderFailure =
        if (code == 400) ProviderFailure.AUTHENTICATION else failureForStatus(code)

    @Serializable
    private data class ModelList(
        @SerialName("models") val models: List<Model> = emptyList(),
    )

    @Serializable
    private data class Model(@SerialName("name") val name: String)
}
