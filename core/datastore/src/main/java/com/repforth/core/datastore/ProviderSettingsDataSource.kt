package com.repforth.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * The non-secret half of the AI provider configuration (§8).
 *
 * **The key is not here and must never be.** This is Preferences DataStore, a
 * plain-text file; §20 requires API keys to be absent from it. The key lives in
 * `core:secrets`, encrypted, and the two halves meet only in a `ProviderConfig`
 * that is assembled per call and never written down. If a `stringPreferencesKey`
 * for anything key-shaped ever appears in this file, that is the bug.
 *
 * Shares the one preferences file rather than opening a second. DataStore allows
 * a single instance per file per process, so a second file would mean a second
 * singleton, a second failure mode, and a second thing to remember on reset —
 * for a handful of scalars that are settings like any other.
 *
 * [baseUrl] and [model] are stored per provider, not once. Switching provider to
 * look at the other one's options and switching back should not silently discard
 * what was typed, and a single shared field is how that happens.
 */
@Singleton
class ProviderSettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<ProviderSettings> = dataStore.data
        // Same reasoning as UserPreferencesDataSource: an unreadable file must
        // give defaults rather than take the app down, and anything that is not
        // an IOException is a programming error worth propagating.
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map(::toProviderSettings)

    suspend fun setProvider(provider: ProviderId) = edit {
        it[Keys.PROVIDER] = provider.name
    }

    /** A blank model resets to the provider's default rather than storing "". */
    suspend fun setModel(provider: ProviderId, model: String) = edit {
        val trimmed = model.trim()
        if (trimmed.isEmpty()) {
            it.remove(Keys.model(provider))
        } else {
            it[Keys.model(provider)] = trimmed
        }
    }

    suspend fun setBaseUrl(baseUrl: String) = edit {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) it.remove(Keys.BASE_URL) else it[Keys.BASE_URL] = trimmed
    }

    suspend fun setRequestTimeoutSeconds(seconds: Int) = edit {
        it[Keys.TIMEOUT_SECONDS] = seconds.coerceIn(
            ProviderSettings.MIN_TIMEOUT_SECONDS,
            ProviderSettings.MAX_TIMEOUT_SECONDS,
        )
    }

    suspend fun setAllowCleartext(allowed: Boolean) = edit {
        it[Keys.ALLOW_CLEARTEXT] = allowed
    }

    /**
     * Removes the provider configuration and nothing else (§8's "delete all
     * provider settings").
     *
     * Named keys rather than `clear()`, because this store is shared with the
     * user's theme, language and units — clearing it would reset the app under
     * the guise of forgetting an endpoint.
     */
    suspend fun clear() = edit { preferences ->
        // The cast is unavoidable: `remove` is generic in the key's value type,
        // and a heterogeneous list of keys has already lost it. Nothing is read
        // back, so the type it is erased to does not matter.
        @Suppress("UNCHECKED_CAST")
        Keys.all().forEach { key -> preferences.remove(key as Preferences.Key<Any>) }
    }

    /**
     * Returns Unit, not the edited [Preferences].
     *
     * `DataStore.edit` hands back what it wrote, and an expression-bodied setter
     * would put that type into this class's signatures — where consumers that
     * depend on this module with `implementation` cannot see it, and so cannot
     * call the setter at all.
     */
    private suspend fun edit(
        block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ) {
        dataStore.edit(block)
    }

    private object Keys {
        val PROVIDER = stringPreferencesKey("ai_provider")
        val BASE_URL = stringPreferencesKey("ai_base_url")
        val TIMEOUT_SECONDS = intPreferencesKey("ai_timeout_seconds")
        val ALLOW_CLEARTEXT = booleanPreferencesKey("ai_allow_cleartext")

        fun model(provider: ProviderId) =
            stringPreferencesKey("ai_model_${provider.name.lowercase()}")

        /**
         * Every key this class owns, so [clear] cannot miss one.
         *
         * Derived from `ProviderId.entries` rather than listed, because a third
         * provider would otherwise leave its model id behind after the user
         * asked for everything to be deleted.
         */
        fun all(): List<Preferences.Key<*>> =
            listOf(PROVIDER, BASE_URL, TIMEOUT_SECONDS, ALLOW_CLEARTEXT) +
                ProviderId.entries.map(::model)
    }

    private companion object {
        fun toProviderSettings(preferences: Preferences): ProviderSettings {
            val provider = preferences[Keys.PROVIDER]
                ?.let { name -> ProviderId.entries.firstOrNull { it.name == name } }
                ?: ProviderSettings.Default.provider

            return ProviderSettings(
                provider = provider,
                model = preferences[Keys.model(provider)]
                    ?: ProviderSettings.defaultModelFor(provider),
                baseUrl = preferences[Keys.BASE_URL] ?: ProviderSettings.Default.baseUrl,
                requestTimeoutSeconds = preferences[Keys.TIMEOUT_SECONDS]
                    ?: ProviderSettings.Default.requestTimeoutSeconds,
                allowCleartext = preferences[Keys.ALLOW_CLEARTEXT]
                    ?: ProviderSettings.Default.allowCleartext,
            )
        }
    }
}
