package com.repforth.core.model

/**
 * Which AI provider the user has chosen (§8).
 *
 * Two, deliberately. Gemini gets a native adapter because its request and
 * response shapes are its own; everything else the guideline commits to is
 * reached through the OpenAI-compatible shape, which is the closest thing to a
 * lingua franca among hosted and local model servers. Providers with genuinely
 * different protocols — Anthropic's native API among them — get their own
 * adapter later rather than being bent into one of these.
 */
enum class ProviderId {
    GEMINI,
    OPENAI_COMPATIBLE,
}

/**
 * Everything about a provider except the secret (§8, §20).
 *
 * **The key is not here, and its absence is the point.** This type is what gets
 * written to ordinary DataStore, which is plain text on disk; the key is
 * encrypted separately through `core:secrets`. Splitting them means a field
 * added here later cannot accidentally become the place someone stores a
 * credential, and `ProviderSettingsFieldsTest` fails if one starts to look like
 * it. The two are joined only in [ProviderConfig], which is never persisted.
 *
 * [baseUrl] is meaningful for [ProviderId.OPENAI_COMPATIBLE] alone. Gemini's
 * endpoint is fixed, and §8 says the field is not even shown for it — but the
 * value is still stored per provider so that switching to the generic provider
 * and back does not silently discard what the user typed.
 */
data class ProviderSettings(
    val provider: ProviderId,
    /** The model id, editable, defaulted per provider. */
    val model: String,
    /** Only meaningful for [ProviderId.OPENAI_COMPATIBLE]; blank means unset. */
    val baseUrl: String,
    val requestTimeoutSeconds: Int,
    /**
     * Permits `http://` to a loopback or private address (§8's developer
     * setting). Off by default, and [EndpointPolicy] still refuses cleartext to
     * anywhere that is not local even when this is on.
     */
    val allowCleartext: Boolean,
) {
    /** The base URL actually used, which is fixed for Gemini. */
    val effectiveBaseUrl: String
        get() = when (provider) {
            ProviderId.GEMINI -> GEMINI_BASE_URL
            ProviderId.OPENAI_COMPATIBLE -> baseUrl
        }

    companion object {
        const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"

        /**
         * Defaults that are current as of writing and will not stay that way.
         *
         * Both are editable in Settings precisely because a model id is the
         * part of this configuration with the shortest shelf life — a provider
         * retiring one must be a text edit, not an app update.
         */
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"

        /**
         * Generous, because generating a workout is not a page load: the model
         * is asked for a structured plan over a candidate list, and a timeout
         * that fires mid-generation costs the user the request and the tokens.
         */
        const val DEFAULT_TIMEOUT_SECONDS = 60
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 300

        /** What a user who has never opened the AI settings has. */
        val Default = ProviderSettings(
            provider = ProviderId.GEMINI,
            model = DEFAULT_GEMINI_MODEL,
            baseUrl = "",
            requestTimeoutSeconds = DEFAULT_TIMEOUT_SECONDS,
            allowCleartext = false,
        )

        /** The stock model id for a provider, used when the field is cleared. */
        fun defaultModelFor(provider: ProviderId): String = when (provider) {
            ProviderId.GEMINI -> DEFAULT_GEMINI_MODEL
            ProviderId.OPENAI_COMPATIBLE -> DEFAULT_OPENAI_MODEL
        }
    }
}

/**
 * A provider's settings together with the key, assembled for one call (§8).
 *
 * §8 requires providers to be stateless and the config to be "resolved per call
 * from encrypted storage, passed in, and never cached in the provider instance
 * or retained beyond the call". So this type exists only in flight: it is not
 * `@Serializable`, nothing writes it anywhere, and `ProviderRepository` builds a
 * fresh one each time.
 *
 * [toString] is overridden. A data class prints every property, so the default
 * one would put the user's API key into any log line, exception message, or
 * debugger view that touched it — which is exactly what §8's "redact
 * authorization headers and prompts from release logs" is about, and the sort
 * of leak that is invisible until it is in a bug report.
 */
class ProviderConfig(
    val settings: ProviderSettings,
    val apiKey: String,
) {
    val provider: ProviderId get() = settings.provider
    val model: String get() = settings.model
    val baseUrl: String get() = settings.effectiveBaseUrl

    override fun toString(): String =
        "ProviderConfig(settings=$settings, apiKey=<redacted>)"
}
