package com.repforth.core.datastore

import androidx.datastore.preferences.core.stringPreferencesKey
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
import com.repforth.core.model.ThemeMode
import com.repforth.core.testing.FakePreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Provider settings, which share a preferences file with everything else (§8).
 *
 * Two properties are worth more than the rest here: that the model id is kept
 * per provider, so looking at the other option and coming back does not lose
 * what was typed; and that "delete all provider settings" deletes provider
 * settings rather than the user's theme.
 */
class ProviderSettingsDataSourceTest {

    private val store = FakePreferencesStore()
    private val source = ProviderSettingsDataSource(store)

    @Test
    fun `a fresh store returns the documented defaults`() = runTest {
        assertEquals(ProviderSettings.Default, source.settings.first())
    }

    @Test
    fun `the model is remembered per provider`() = runTest {
        source.setModel(ProviderId.GEMINI, "gemini-experimental")
        source.setModel(ProviderId.OPENAI_COMPATIBLE, "llama3.1")

        source.setProvider(ProviderId.OPENAI_COMPATIBLE)
        assertEquals("llama3.1", source.settings.first().model)

        source.setProvider(ProviderId.GEMINI)
        assertEquals(
            "Switching away and back must not lose the other model id",
            "gemini-experimental",
            source.settings.first().model,
        )
    }

    @Test
    fun `clearing the model restores the provider default`() = runTest {
        source.setModel(ProviderId.GEMINI, "gemini-experimental")
        source.setModel(ProviderId.GEMINI, "   ")

        assertEquals(
            ProviderSettings.DEFAULT_GEMINI_MODEL,
            source.settings.first().model,
        )
    }

    @Test
    fun `a timeout outside the allowed range is clamped rather than stored`() = runTest {
        source.setRequestTimeoutSeconds(100_000)
        assertEquals(
            ProviderSettings.MAX_TIMEOUT_SECONDS,
            source.settings.first().requestTimeoutSeconds,
        )

        source.setRequestTimeoutSeconds(0)
        assertEquals(
            "A zero timeout would fail every request instantly",
            ProviderSettings.MIN_TIMEOUT_SECONDS,
            source.settings.first().requestTimeoutSeconds,
        )
    }

    /**
     * §8 lists "delete all provider settings" next to "delete key", which means
     * the AI configuration — not the app's settings.
     *
     * Watched failing with this class's `clear()` calling DataStore's own
     * `clear()`: the theme came back SYSTEM, so a user forgetting an endpoint
     * would silently have had appearance, language and units reset with it.
     */
    @Test
    fun `clearing provider settings leaves the rest of the preferences alone`() = runTest {
        val preferences = UserPreferencesDataSource(store)
        preferences.setThemeMode(ThemeMode.DARK)
        source.setProvider(ProviderId.OPENAI_COMPATIBLE)
        source.setBaseUrl("https://api.example.com/v1/")

        source.clear()

        assertEquals(ProviderSettings.Default, source.settings.first())
        assertEquals(
            "The theme is not a provider setting",
            ThemeMode.DARK,
            preferences.preferences.first().themeMode,
        )
    }

    /**
     * A provider added later gets a model key of its own, and `clear()` is
     * derived from the enum so it cannot miss one.
     */
    @Test
    fun `clearing removes the model of every provider, not just the selected one`() = runTest {
        ProviderId.entries.forEach { source.setModel(it, "custom-model") }

        source.clear()

        ProviderId.entries.forEach { provider ->
            source.setProvider(provider)
            assertEquals(
                "$provider kept a model id through a delete-everything",
                ProviderSettings.defaultModelFor(provider),
                source.settings.first().model,
            )
        }
    }

    /**
     * What a device has after a provider constant is renamed or removed. The
     * user should get the default back, not a crash on the settings screen.
     */
    @Test
    fun `an unknown provider name falls back to the default rather than throwing`() = runTest {
        store.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                set(stringPreferencesKey("ai_provider"), "ANTHROPIC_NATIVE")
            }
        }

        assertEquals(ProviderSettings.Default.provider, source.settings.first().provider)
    }
}
