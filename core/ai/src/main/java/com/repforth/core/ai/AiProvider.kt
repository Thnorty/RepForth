package com.repforth.core.ai

import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId

/**
 * Why a provider could not be reached, in the categories §8 asks to be
 * actionable.
 *
 * Categories rather than a message, because the message is the provider's and
 * the advice is ours: only this app knows that a refused endpoint means "check
 * the address field" and a quota error means "this is your account, not this
 * app". A raw string passed to the UI would be neither translated nor useful.
 */
enum class ProviderFailure {
    /** The key was rejected, or there is no key. */
    AUTHENTICATION,

    /** Reached and authenticated, but the configured model is not offered. */
    MODEL_NOT_FOUND,

    /** Rate limited or out of credit — the user's account, not this app. */
    QUOTA,

    /** Never got there: no connection, DNS, TLS, or the request timed out. */
    NETWORK,

    /** Got there and could not understand the reply. */
    FORMAT,

    /** No usable endpoint was configured; this is not an address policy (§8). */
    ENDPOINT_REFUSED,

    /** The provider itself is broken right now. */
    SERVER,
}

sealed interface ProviderTestResult {
    /**
     * The provider answered and accepted the key.
     *
     * [modelConfirmed] is false when the server does not offer a model list to
     * check against — several OpenAI-compatible servers do not. That is not a
     * failure, and reporting it as one would tell the user to fix something
     * that is not broken; it is reported as "connected, could not confirm the
     * model", which is exactly what happened.
     */
    data class Ok(val modelConfirmed: Boolean) : ProviderTestResult

    /** [detail] is for the log and the bug report, never for translation. */
    data class Failed(
        val failure: ProviderFailure,
        val detail: String? = null,
    ) : ProviderTestResult
}

sealed interface ProviderGenerationResult {
    data class Ok(val response: AiWorkoutResponse) : ProviderGenerationResult

    /** [detail] is redacted diagnostic context, never the provider body. */
    data class Failed(
        val failure: ProviderFailure,
        val detail: String? = null,
    ) : ProviderGenerationResult
}

/**
 * One AI provider, as §8 defines it.
 *
 * Providers are stateless. [ProviderConfig] is resolved per call from encrypted
 * storage, passed in, and never held: an implementation that cached it would be
 * keeping a plaintext key alive for the life of the process, which is the thing
 * `core:secrets` exists to avoid.
 *
 * Generation returns a typed outcome rather than throwing for provider,
 * network, or format failures. The later orchestration layer can therefore
 * retry or fall back without parsing exceptions or provider messages.
 */
interface AiProvider {
    val id: ProviderId

    /**
     * Asks the provider whether it is reachable, the key works, and the model
     * exists — the three things that can be wrong, told apart.
     *
     * Never throws for an expected failure. A network error and a bad key are
     * both ordinary outcomes of pressing "Test connection", and a screen should
     * not have to catch exceptions to render them.
     */
    suspend fun testConnection(config: ProviderConfig): ProviderTestResult

    /** Requests one structured workout; validation happens at the next boundary. */
    suspend fun generateWorkout(
        config: ProviderConfig,
        request: AiWorkoutRequest,
        retryFeedback: AiWorkoutRetryFeedback? = null,
    ): ProviderGenerationResult
}
