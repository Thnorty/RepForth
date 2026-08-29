package com.repforth.core.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An in-memory [DataStore], for testing anything that stores preferences.
 *
 * A file-backed store was tried first and fails on a Windows host: DataStore's
 * `java.io` storage renames a temp file onto the target, and Windows refuses
 * that once the target exists, so every second write threw. Rather than a test
 * that passes on CI and fails on the maintainer's machine, this exercises the
 * same public API against storage that behaves identically everywhere.
 *
 * What it gives up is real persistence. Whether the bytes survive a restart is
 * DataStore's guarantee rather than this project's, and confirming it needs a
 * device.
 *
 * Lives here rather than inside one module's tests because two modules now need
 * it, and the second one nearly got a copy.
 */
class FakePreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(state.value).also { state.value = it }
}
