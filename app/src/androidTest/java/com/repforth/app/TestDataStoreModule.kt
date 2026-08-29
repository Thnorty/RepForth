package com.repforth.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.repforth.core.datastore.di.DataStoreModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Preferences in memory, for instrumentation.
 *
 * Two reasons, and the second is the one that made this necessary rather than
 * merely tidy.
 *
 * DataStore permits exactly one instance per file per process and throws on a
 * second. Each test method builds a fresh Hilt component, so the real module
 * opened `user_preferences.preferences_pb` again while the previous instance
 * still held it, and every test after the first failed with "There are multiple
 * DataStores active for the same file".
 *
 * And a test that reads the device's real preferences is not a test of
 * anything: it passes or fails depending on what the last person to hold the
 * phone did. In memory means every run starts from the documented defaults.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(): DataStore<Preferences> = InMemoryPreferences()
}

/**
 * The same idea as `core:testing`'s `FakePreferencesStore`, written out again
 * here on purpose.
 *
 * `core:testing` exists for JVM unit tests and exposes JUnit with `api`.
 * Depending on it from `androidTest` drags that into an APK, and dexing refuses
 * — rightly, since a test-fixture module for one source set has no business
 * being packaged for another. Eight lines is the cheaper of the two costs.
 */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(state.value).also { state.value = it }
}
