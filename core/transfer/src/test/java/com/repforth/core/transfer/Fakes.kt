package com.repforth.core.transfer

import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.workout.SessionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * In-memory stand-ins for the three repositories and the preference store.
 *
 * Real enough to be worth testing against: they replace by id the way the Room
 * implementations do, so "import a plan that already exists" behaves here as it
 * will on a device.
 */

internal class FakeProfiles : ProfileRepository {
    var stored: UserProfile? = null
    private val flow = MutableStateFlow<UserProfile?>(null)

    override fun observeProfile(): Flow<UserProfile?> = flow

    override suspend fun getProfile(): UserProfile? = stored

    override suspend fun save(profile: UserProfile) {
        stored = profile
        flow.value = profile
    }

    override suspend fun deleteAll() {
        stored = null
        flow.value = null
    }
}

internal class FakeTemplates : TemplateRepository {
    val stored = mutableListOf<WorkoutTemplate>()
    private val flow = MutableStateFlow<List<WorkoutTemplate>>(emptyList())

    override fun observeAll(): Flow<List<WorkoutTemplate>> = flow

    override suspend fun find(id: String): WorkoutTemplate? = stored.firstOrNull { it.id == id }

    override suspend fun save(template: WorkoutTemplate) {
        stored.removeAll { it.id == template.id }
        stored += template
        flow.value = stored.toList()
    }

    override suspend fun delete(id: String) {
        stored.removeAll { it.id == id }
        flow.value = stored.toList()
    }

    override suspend fun deleteAll() {
        stored.clear()
        flow.value = emptyList()
    }
}

internal class FakeSessions : SessionRepository {
    val stored = mutableListOf<SessionSnapshot>()
    private val completed = MutableStateFlow<List<SessionSnapshot>>(emptyList())

    override fun observeActive(): Flow<SessionSnapshot?> = MutableStateFlow(null)

    override suspend fun restoreActive(): SessionSnapshot? = null

    override fun observeCompleted(): Flow<List<SessionSnapshot>> = completed

    override suspend fun persist(snapshot: SessionSnapshot) {
        stored.removeAll { it.sessionId == snapshot.sessionId }
        stored += snapshot
        completed.value = stored.filter { it.phase.isTerminal }
    }

    override suspend fun deleteAll() {
        stored.clear()
        completed.value = emptyList()
    }
}

/**
 * A real [UserPreferencesDataSource] over in-memory storage.
 *
 * Not a stub: `clear()` is the method under test, so faking it would test
 * nothing. This exercises the real implementation and lets the test assert on
 * what the store actually holds afterwards.
 */
internal fun fakePreferences() = UserPreferencesDataSource(FakePreferencesStore())
