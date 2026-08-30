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

    /** This app refused to send: the address is not one it will talk to (§8). */
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

/**
 * One AI provider, as §8 defines it.
 *
 * Providers are stateless. [ProviderConfig] is resolved per call from encrypted
 * storage, passed in, and never held: an implementation that cached it would be
 * keeping a plaintext key alive for the life of the process, which is the thing
 * `core:secrets` exists to avoid.
 *
 * **Only [testConnection] so far, and that is deliberate.** The shared workout
 * contract and validator now exist, but the provider-specific envelope, retry,
 * and fallback path do not. The generation method lands with those pieces so an
 * interface method is never present without a complete caller and outcome.
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
}
