package com.repforth.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.model.Language
import com.repforth.core.model.ThemeMode
import com.repforth.core.model.UnitSystem
import com.repforth.core.model.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Covers the part of preference storage this project owns: the mapping between
 * `Preferences` keys and [UserPreferences], the defaults, and the fallbacks.
 *
 * Backed by [FakePreferencesStore] rather than a file; the reasoning for that,
 * and what it gives up, is documented there.
 */
class UserPreferencesDataSourceTest {

    private fun store(): DataStore<Preferences> = FakePreferencesStore()

    private fun source() = UserPreferencesDataSource(store())

    @Test
    fun `a fresh install gets the documented defaults`() = runTest {
        // The point of this test: a wrong default is invisible until someone
        // installs the app for the first time, by which point it has shipped.
        assertEquals(UserPreferences.Default, source().preferences.first())
    }

    @Test
    fun `the documented defaults are the ones the product intends`() {
        // Pinned separately from the round-trip so that changing a default is a
        // deliberate edit here, not a silent consequence of editing the model.
        val defaults = UserPreferences.Default
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(null, defaults.language)
        assertEquals(UnitSystem.METRIC, defaults.unitSystem)
        assertEquals(true, defaults.keepScreenOn)
        assertEquals(false, defaults.reducedMotion)
        assertEquals(true, defaults.hapticsEnabled)
        assertEquals(false, defaults.onboardingComplete)
        assertEquals(true, defaults.mediaWifiOnly)
    }

    @Test
    fun `every preference round-trips`() = runTest {
        val source = source()
        source.setThemeMode(ThemeMode.DARK)
        source.setLanguage(Language.TURKISH)
        source.setUnitSystem(UnitSystem.IMPERIAL)
        source.setKeepScreenOn(false)
        source.setReducedMotion(true)
        source.setHapticsEnabled(false)
        source.setOnboardingComplete(true)
        source.setMediaWifiOnly(false)

        assertEquals(
            UserPreferences(
                themeMode = ThemeMode.DARK,
                language = Language.TURKISH,
                unitSystem = UnitSystem.IMPERIAL,
                keepScreenOn = false,
                reducedMotion = true,
                hapticsEnabled = false,
                onboardingComplete = true,
                mediaWifiOnly = false,
            ),
            source.preferences.first(),
        )
    }

    @Test
    fun `clearing the language override restores follow-the-system`() = runTest {
        val source = source()
        source.setLanguage(Language.TURKISH)
        assertEquals(Language.TURKISH, source.preferences.first().language)

        source.setLanguage(null)
        assertEquals(
            "null means follow the system locale, and must survive being set explicitly",
            null,
            source.preferences.first().language,
        )
    }

    @Test
    fun `a value written by an older version falls back to its default`() = runTest {
        // Enum names are persisted as strings. A constant renamed or removed in
        // a later release leaves an unparseable value on devices that already
        // wrote the old one; the user should get the default, not a crash.
        val dataStore = store()
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "MIDNIGHT_NEON" }

        val loaded = UserPreferencesDataSource(dataStore).preferences.first()
        assertEquals(UserPreferences.Default.themeMode, loaded.themeMode)
    }

    @Test
    fun `an unknown language tag falls back to follow-the-system`() = runTest {
        val dataStore = store()
        dataStore.edit { it[stringPreferencesKey("language")] = "de" }

        assertEquals(null, UserPreferencesDataSource(dataStore).preferences.first().language)
    }
}
