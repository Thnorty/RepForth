package com.repforth.core.transfer

import com.repforth.core.ai.ProviderRepository
import com.repforth.core.datastore.ProviderSettingsDataSource
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.testing.InMemorySecretStore
import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.userdata.UserDataTransaction
import com.repforth.core.userdata.WeekRepository
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

internal class FakeWeeks : WeekRepository {
    val stored = mutableListOf<TrainingWeek>()
    private val flow = MutableStateFlow<List<TrainingWeek>>(emptyList())
    private val activeFlow = MutableStateFlow<TrainingWeek?>(null)

    override fun observeAll(): Flow<List<TrainingWeek>> = flow

    override fun observeActive(): Flow<TrainingWeek?> = activeFlow

    override suspend fun find(id: String): TrainingWeek? = stored.firstOrNull { it.id == id }

    override suspend fun save(week: TrainingWeek) {
        stored.removeAll { it.id == week.id }
        if (week.active) {
            stored.replaceAll { it.copy(active = false) }
        }
        stored += week
        flow.value = stored.toList()
        activeFlow.value = stored.firstOrNull { it.active }
    }

    override suspend fun setActive(id: String) {
        stored.replaceAll { it.copy(active = it.id == id) }
        flow.value = stored.toList()
        activeFlow.value = stored.firstOrNull { it.active }
    }

    override suspend fun delete(id: String) {
        stored.removeAll { it.id == id }
        flow.value = stored.toList()
        activeFlow.value = stored.firstOrNull { it.active }
    }

    override suspend fun deleteAll() {
        stored.clear()
        flow.value = emptyList()
        activeFlow.value = null
    }
}

internal class FakeSessions : SessionRepository {
    val stored = mutableListOf<SessionSnapshot>()
    private val completed = MutableStateFlow<List<SessionSnapshot>>(emptyList())

    override fun observeActive(): Flow<SessionSnapshot?> = MutableStateFlow(null)

    override suspend fun restoreActive(): SessionSnapshot? = null

    override fun observeFinished(): Flow<List<SessionSnapshot>> = completed

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

/**
 * A real [ProviderRepository] over in-memory storage, and the secret store it
 * writes to, so a test can look at both sides of a reset.
 *
 * The store is returned alongside the repository because the question worth
 * asking after `resetApp()` is not "does the repository say there is no key" —
 * it is "is there still ciphertext". Those are different questions, and only
 * the second one is about the user's phone.
 */
internal fun fakeProviders(): Pair<ProviderRepository, InMemorySecretStore> {
    val secrets = InMemorySecretStore()
    val settings = ProviderSettingsDataSource(FakePreferencesStore())
    return ProviderRepository(settings, secrets) to secrets
}

/**
 * A transaction that only sequences, because these fakes are not a database.
 *
 * Enough for every test about *what* the import writes. It is deliberately not
 * enough for the test about rollback — that one supplies its own
 * [UserDataTransaction] which throws, and asserts the fakes it rolls back are
 * the ones it rolled back itself. Faking atomicity here would prove the fake.
 * The real one is `RoomUserDataTransaction` over `RepForthDatabase`.
 */
internal object DirectTransaction : UserDataTransaction {
    override suspend fun <R> run(block: suspend () -> R): R = block()
}

/** What the four repositories held, so a rollback has something to restore. */
internal data class StoredState(
    val templates: List<WorkoutTemplate>,
    val weeks: List<TrainingWeek>,
    val sessions: List<SessionSnapshot>,
    val profile: UserProfile?,
)

/**
 * A transaction that fails, and really does undo what the block had written.
 *
 * The fakes have no rollback of their own, so this puts the recorded state back
 * — which is what makes the assertion afterwards mean something. Without it the
 * test would pass whether or not `import` had a transaction around it at all,
 * because the throw would simply stop the writes early and the earlier ones
 * would still be sitting in the fakes. That is the exact bug, so a test that
 * cannot tell the difference is worse than none.
 */
internal class RollingBackTransaction(
    private val before: StoredState,
    private val profiles: FakeProfiles,
    private val templates: FakeTemplates,
    private val weeks: FakeWeeks,
    private val sessions: FakeSessions,
) : UserDataTransaction {

    override suspend fun <R> run(block: suspend () -> R): R {
        try {
            block()
        } catch (e: Exception) {
            restore()
            throw e
        }
        restore()
        throw IllegalStateException("could not be saved")
    }

    private suspend fun restore() {
        templates.deleteAll()
        before.templates.forEach { templates.save(it) }
        weeks.deleteAll()
        before.weeks.forEach { weeks.save(it) }
        sessions.deleteAll()
        before.sessions.forEach { sessions.persist(it) }
        profiles.deleteAll()
        before.profile?.let { profiles.save(it) }
    }
}
