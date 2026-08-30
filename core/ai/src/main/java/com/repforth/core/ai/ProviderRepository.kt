package com.repforth.core.ai

import com.repforth.core.datastore.ProviderSettingsDataSource
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
import com.repforth.core.secrets.SecretId
import com.repforth.core.secrets.SecretStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The one place a provider's settings and its key are brought together (§8).
 *
 * Everything above this sees either non-secret settings or a fully assembled
 * [ProviderConfig] for a single call. Nothing above this touches [SecretStore],
 * which is why the key cannot end up somewhere it should not be: there is one
 * reader, and it is this file.
 *
 * §8 requires the config to be "resolved per call from encrypted storage, passed
 * in, and never cached in the provider instance or retained beyond the call", so
 * [configFor] decrypts on every call and hands back a value nobody stores. The
 * decryption cost is a file read and an AES-GCM open; the alternative is a
 * long-lived plaintext key sitting in a singleton for the life of the process.
 */
@Singleton
class ProviderRepository @Inject constructor(
    private val store: ProviderSettingsDataSource,
    private val secrets: SecretStore,
) {
    /**
     * Bumped whenever a key is written or removed.
     *
     * [SecretStore] has no change notification and deliberately no way to list
     * what it holds, so "is a key stored?" cannot be observed the way a
     * preference can. This counter is what turns a one-shot question into
     * something the settings screen can collect — cheaper than polling, and it
     * keeps the store's contract narrow.
     */
    private val keyRevision = MutableStateFlow(0)

    val settings: Flow<ProviderSettings> = store.settings

    /** Whether a key is stored for the currently selected provider. */
    val hasKey: Flow<Boolean> = combine(store.settings, keyRevision) { settings, _ ->
        secrets.contains(settings.provider.secretId())
    }

    /** Which providers have a key, so switching provider can say what is missing. */
    val providersWithKeys: Flow<Set<ProviderId>> = keyRevision.map {
        ProviderId.entries.filterTo(mutableSetOf()) { secrets.contains(it.secretId()) }
    }

    suspend fun setProvider(provider: ProviderId) = store.setProvider(provider)

    suspend fun setModel(provider: ProviderId, model: String) = store.setModel(provider, model)

    suspend fun setBaseUrl(baseUrl: String) = store.setBaseUrl(baseUrl)

    suspend fun setRequestTimeoutSeconds(seconds: Int) =
        store.setRequestTimeoutSeconds(seconds)


    suspend fun setAllowCleartext(allowed: Boolean) = store.setAllowCleartext(allowed)

    /**
     * Stores a key for [provider].
     *
     * A blank key deletes rather than storing an empty string: a stored ""
     * would make [hasKey] true and every request fail with an authentication
     * error the user could not explain.
     */
    suspend fun setKey(provider: ProviderId, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            secrets.delete(provider.secretId())
        } else {
            secrets.put(provider.secretId(), trimmed)
        }
        keyRevision.update { it + 1 }
    }

    /** §8's "delete key" — this provider's secret, leaving its settings alone. */
    suspend fun deleteKey(provider: ProviderId) {
        secrets.delete(provider.secretId())
        keyRevision.update { it + 1 }
    }

    /**
     * §8's "delete all provider settings", and part of §7's "reset app".
     *
     * Secrets first. If the process dies between the two writes, what survives
     * is settings pointing at a provider with no key — recoverable, and visible
     * in the UI. The other order leaves an encrypted key with nothing naming it,
     * which is the state nobody would think to look for.
     */
    suspend fun deleteAll() {
        secrets.clear()
        keyRevision.update { it + 1 }
        store.clear()
    }

    /**
     * The configuration for one call, or null when no key is stored.
     *
     * Null rather than an exception: "the user has not set this up" is an
     * ordinary state that the caller answers by falling back to the rules
     * engine (§8, step 8), not an error worth a stack trace.
     */
    suspend fun configFor(settings: ProviderSettings): ProviderConfig? {
        val key = secrets.get(settings.provider.secretId()) ?: return null
        return ProviderConfig(settings = settings, apiKey = key)
    }
}

/**
 * One slot per provider, so switching does not overwrite the other's key.
 *
 * The id is also the AEAD's associated data in `KeystoreSecretStore`, which
 * means ciphertext written for one provider cannot be read back as another's.
 * Changing this string orphans stored keys, which reads to the user as "my key
 * disappeared" — so it is derived from the enum constant and left alone.
 */
internal fun ProviderId.secretId() = SecretId("provider.${name.lowercase()}.key")
