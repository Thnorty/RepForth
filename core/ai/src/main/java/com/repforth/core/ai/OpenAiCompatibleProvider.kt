package com.repforth.core.ai

import com.repforth.core.ai.http.ProviderHttp
import com.repforth.core.ai.http.PROVIDER_JSON_MEDIA_TYPE
import com.repforth.core.ai.http.failureForStatus
import com.repforth.core.ai.http.generationTimeoutSeconds
import com.repforth.core.ai.http.providerEndpoint
import com.repforth.core.ai.http.toProviderFailure
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anything that speaks the OpenAI request and response shape (§8).
 *
 * §8 is honest that this is not a universal protocol: "'All AI endpoints' is not
 * a realistic single protocol. The generic adapter should support services that
 * implement the relevant OpenAI-compatible request/response shape." So this
 * adapter is written to that shape and nothing else — a provider with its own
 * protocol gets its own adapter rather than a special case in here.
 *
 * The address comes from the user. [ProviderHttp] applies the shared timeout,
 * cancellation, and failure mapping; it deliberately does not police the URL.
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

        val request = runCatching {
            Request.Builder()
                .url(providerEndpoint(config.baseUrl, "models"))
                .withOptionalApiKey(config)
                .get()
                .build()
        }.getOrElse {
            return ProviderTestResult.Failed(
                ProviderFailure.ENDPOINT_REFUSED,
                "invalid address",
            )
        }

        val reply = http.send(
            request = request,
            timeoutSeconds = config.settings.requestTimeoutSeconds,
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

    override suspend fun generateWorkout(
        config: ProviderConfig,
        request: AiWorkoutRequest,
        retryFeedback: AiWorkoutRetryFeedback?,
    ): ProviderGenerationResult {
        if (config.baseUrl.isBlank()) {
            return ProviderGenerationResult.Failed(ProviderFailure.ENDPOINT_REFUSED, "no address")
        }

        val wireRequest = runCatching {
            val body = ChatCompletionRequest(
                model = config.model,
                messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = request.toGenerationPrompt(retryFeedback),
                    ),
                ),
                responseFormat = ChatResponseFormat(
                    type = "json_schema",
                    jsonSchema = ChatJsonSchema(
                        name = AI_WORKOUT_SCHEMA_NAME,
                        strict = true,
                        schema = AiWorkoutJsonSchema.value,
                    ),
                ),
            )
            Request.Builder()
                .url(providerEndpoint(config.baseUrl, "chat/completions"))
                .withOptionalApiKey(config)
                .post(json.encodeToString(body).toRequestBody(PROVIDER_JSON_MEDIA_TYPE))
                .build()
        }.getOrElse {
            return ProviderGenerationResult.Failed(
                ProviderFailure.ENDPOINT_REFUSED,
                "invalid address",
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
                failureForStatus(reply.code),
                providerDetail(reply.body),
            )
        }

        val envelope = runCatching {
            json.decodeFromString<ChatCompletionResponse>(reply.body)
        }.getOrElse {
            return ProviderGenerationResult.Failed(
                ProviderFailure.FORMAT,
                "Invalid OpenAI-compatible response envelope",
            )
        }
        val message = envelope.choices.firstOrNull()?.message
            ?: return ProviderGenerationResult.Failed(
                ProviderFailure.FORMAT,
                "OpenAI-compatible response contained no choice",
            )
        if (!message.refusal.isNullOrBlank()) {
            return ProviderGenerationResult.Failed(
                ProviderFailure.FORMAT,
                "Provider refused workout generation",
            )
        }
        val structured = message.content
            ?: return ProviderGenerationResult.Failed(
                ProviderFailure.FORMAT,
                "OpenAI-compatible response contained no structured workout",
            )

        return when (val decoded = AiWorkoutCodec.decodeResponse(structured)) {
            is AiWorkoutDecodeResult.Ok -> ProviderGenerationResult.Ok(decoded.response)
            AiWorkoutDecodeResult.Malformed -> ProviderGenerationResult.Failed(
                ProviderFailure.FORMAT,
                "OpenAI-compatible provider returned a malformed structured workout",
            )
        }
    }

    @Serializable
    private data class ModelList(
        @SerialName("data") val data: List<Model> = emptyList(),
    )

    @Serializable
    private data class Model(@SerialName("id") val id: String)

    /** A keyless local server receives no malformed empty credential. */
    private fun Request.Builder.withOptionalApiKey(config: ProviderConfig): Request.Builder =
        apply {
            if (config.apiKey.isNotBlank()) {
                header("Authorization", "Bearer ${config.apiKey}")
            }
        }

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        @SerialName("response_format") val responseFormat: ChatResponseFormat,
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChatResponseFormat(
        val type: String,
        @SerialName("json_schema") val jsonSchema: ChatJsonSchema,
    )

    @Serializable
    private data class ChatJsonSchema(
        val name: String,
        val strict: Boolean,
        val schema: JsonObject,
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<ChatChoice> = emptyList(),
    )

    @Serializable
    private data class ChatChoice(val message: ChatResponseMessage? = null)

    @Serializable
    private data class ChatResponseMessage(
        val content: String? = null,
        val refusal: String? = null,
    )

    /** The response body exactly as it arrived. See GeminiProvider.providerDetail. */
    private fun providerDetail(body: String): String? =
        body.trim().takeIf { it.isNotEmpty() }?.take(PROVIDER_DETAIL_MAX_CHARS)

}
