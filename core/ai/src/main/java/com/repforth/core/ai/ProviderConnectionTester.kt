package com.repforth.core.ai

import com.repforth.core.model.ProviderId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * §8's "Test connection", from the screen's point of view.
 *
 * The screen knows nothing about which adapter is involved, and nothing about
 * the key. It asks whether the current configuration works; this resolves the
 * config, picks the adapter, and answers.
 *
 * The config is fetched here and dropped when the call returns — §8 requires it
 * to be "resolved per call from encrypted storage, passed in, and never cached
 * in the provider instance or retained beyond the call". The adapters take it as
 * a parameter for exactly that reason.
 */
@Singleton
class ProviderConnectionTester @Inject constructor(
    private val repository: ProviderRepository,
    private val adapters: Map<ProviderId, @JvmSuppressWildcards AiProvider>,
) {
    suspend fun test(): ProviderTestResult {
        val settings = repository.settings.first()
        val config = repository.configFor(settings)
            // No key is not a network failure, and saying so would send the
            // user to check their connection over a field they have not filled
            // in yet.
            ?: return ProviderTestResult.Failed(ProviderFailure.AUTHENTICATION, "no key stored")

        val adapter = adapters[settings.provider]
            ?: return ProviderTestResult.Failed(
                ProviderFailure.ENDPOINT_REFUSED,
                "no adapter for ${settings.provider}",
            )

        return adapter.testConnection(config)
    }
}
