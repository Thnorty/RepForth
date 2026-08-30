package com.repforth.core.ai

import com.repforth.core.ai.http.ProviderHttp
import com.repforth.core.ai.http.failureForStatus
import com.repforth.core.ai.http.toProviderFailure
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * Anything that speaks the OpenAI request and response shape (§8).
 *
 * §8 is honest that this is not a universal protocol: "'All AI endpoints' is not
 * a realistic single protocol. The generic adapter should support services that
 * implement the relevant OpenAI-compatible request/response shape." So this
 * adapter is written to that shape and nothing else — a provider with its own
 * protocol gets its own adapter rather than a special case in here.
 *
 * The address comes from the user, which is why every request goes through
 * [ProviderHttp] and its endpoint check rather than straight to OkHttp.
 */
internal class OpenAiCompatibleProvider(
    private val http: ProviderHttp,
    private val json: Json,
) : AiProvider {

    override val id = ProviderId.OPENAI_COMPATIBLE

    override suspend fun testConnection(config: ProviderConfig): ProviderTestResult {
        if (config.baseUrl.isBlank()) {
            return ProviderTestResult.Failed(ProviderFailure.ENDPOINT_REFUSED, "no address")
        }

        val request = Request.Builder()
            .url(config.baseUrl + "models")
            .apply {
                // Omitted entirely when there is no key. `Bearer ` with nothing
                // after it is a malformed credential: some servers reject it
                // outright, and none treat it as "no credential offered",
                // which is what a local model server is expecting.
                if (config.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .get()
            .build()

        val reply = http.send(
            request = request,
            timeoutSeconds = config.settings.requestTimeoutSeconds,
            allowCleartext = config.settings.allowCleartext,
        ).getOrElse { cause ->
            return ProviderTestResult.Failed(cause.toProviderFailure(), cause.message)
        }

        // Plenty of compatible servers implement the chat endpoint and not this
        // one. A 404 here means "cannot check the model", not "wrong key" —
        // reporting it as a failure would send the user to fix something that
        // is working.
        if (reply.code == 404) return ProviderTestResult.Ok(modelConfirmed = false)

        if (reply.code !in 200..299) {
            return ProviderTestResult.Failed(
                failureForStatus(reply.code),
                "HTTP ${reply.code}",
            )
        }

        val models = runCatching { json.decodeFromString<ModelList>(reply.body) }
            .getOrElse {
                return ProviderTestResult.Failed(ProviderFailure.FORMAT, it.message)
            }
            .data
            .map { it.id }

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

    @Serializable
    private data class ModelList(
        @SerialName("data") val data: List<Model> = emptyList(),
    )

    @Serializable
    private data class Model(@SerialName("id") val id: String)
}
