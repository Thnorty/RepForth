package com.repforth.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.repforth.core.model.Language
import com.repforth.core.model.ThemeMode
import com.repforth.core.model.UnitSystem
import com.repforth.core.model.UserPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Reads and writes the user's non-secret settings (§7).
 *
 * Preferences DataStore, not Proto: the guideline picks it, and the shape here
 * is a handful of independent scalars rather than a nested document.
 *
 * Never store a credential through this class. API-key material is encrypted
 * separately (§7, §20) and ordinary DataStore is plain-text on disk — the type
 * signatures below only accept the preference types from `core:model`, which is
 * the cheapest available guard against someone reaching for a generic setter.
 */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data
        // A read failure must not take the app down. DataStore signals a
        // corrupt or unreadable file as IOException; falling back to an empty
        // set means the user sees defaults rather than a crash, and their next
        // write repairs the file. Anything that is NOT an IOException is a
        // programming error and is deliberately allowed to propagate.
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map(::toUserPreferences)

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    /** `null` restores "follow the system locale" by removing the override. */
    suspend fun setLanguage(language: Language?) = edit {
        if (language == null) it.remove(Keys.LANGUAGE) else it[Keys.LANGUAGE] = language.tag
    }

    suspend fun setUnitSystem(system: UnitSystem) = edit { it[Keys.UNIT_SYSTEM] = system.name }

    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = enabled }

    suspend fun setReducedMotion(enabled: Boolean) = edit { it[Keys.REDUCED_MOTION] = enabled }

    suspend fun setHapticsEnabled(enabled: Boolean) = edit { it[Keys.HAPTICS] = enabled }

    suspend fun setMediaWifiOnly(enabled: Boolean) = edit { it[Keys.MEDIA_WIFI_ONLY] = enabled }

    /**
     * Forgets every preference (§7's "reset app").
     *
     * `clear()` rather than writing each default back, so a key added later is
     * removed without anyone remembering to add it here. Reading afterwards
     * yields [UserPreferences.Default], because absence is what the defaults
     * mean.
     */
    suspend fun clear() = edit { it.clear() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val MEDIA_WIFI_ONLY = booleanPreferencesKey("media_wifi_only")
    }

    private companion object {
        /**
         * Every unreadable value falls back to its default rather than throwing.
         *
         * Enum names are persisted as strings, so a renamed or removed constant
         * turns into an unparseable value on a device that already has the old
         * one written. A user who once chose IMPERIAL and then upgrades should
         * get the default back, not a crash on launch.
         */
        fun toUserPreferences(preferences: Preferences) = UserPreferences(
            themeMode = preferences[Keys.THEME_MODE]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: UserPreferences.Default.themeMode,
            language = preferences[Keys.LANGUAGE]?.let(Language::fromTag),
            unitSystem = preferences[Keys.UNIT_SYSTEM]
                ?.let { name -> UnitSystem.entries.firstOrNull { it.name == name } }
                ?: UserPreferences.Default.unitSystem,
            keepScreenOn = preferences[Keys.KEEP_SCREEN_ON]
                ?: UserPreferences.Default.keepScreenOn,
            reducedMotion = preferences[Keys.REDUCED_MOTION]
                ?: UserPreferences.Default.reducedMotion,
            hapticsEnabled = preferences[Keys.HAPTICS]
                ?: UserPreferences.Default.hapticsEnabled,
            mediaWifiOnly = preferences[Keys.MEDIA_WIFI_ONLY]
                ?: UserPreferences.Default.mediaWifiOnly,
        )
    }
}
