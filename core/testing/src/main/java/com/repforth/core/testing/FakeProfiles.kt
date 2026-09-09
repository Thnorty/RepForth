package com.repforth.core.testing

import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.userdata.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A profile repository that keeps one profile in memory.
 *
 * **This existed six times before it existed once**, in four shapes, and the
 * differences were not deliberate — they were whatever each test happened to
 * need on the day. Two of them were quietly weaker than the real repository:
 *
 * - one returned a **new** `MutableStateFlow` from every `observeProfile()`
 *   call, so a collector never saw a later save;
 * - two made `save` a no-op, so a profile written during a test was not the
 *   profile read back.
 *
 * Neither could make a test fail wrongly. Both could let one pass that should
 * not have — a view model that ignored a profile change had nothing to reveal
 * it. This one behaves like the real thing: one flow, and a save that is
 * observable.
 *
 * It lives here, beside `FakePreferencesStore` and `InMemorySecretStore`, which
 * is where this repo already keeps shared fakes. A Gradle test fixture on
 * `core:user-data` would have been tighter — only the modules that asked would
 * pay for it — and it does not work: the Kotlin plugin creates no compilation
 * for a `testFixtures` source set in this AGP pairing, so the file compiled into
 * nothing and every import of it failed. Measured, not assumed: there is no
 * `compileDebugTestFixturesKotlin` task and no class file.
 */
class FakeProfiles(initial: UserProfile? = null) : ProfileRepository {

    private val flow = MutableStateFlow(initial)

    /** Every profile handed to [save], oldest first, for tests that assert on writes. */
    val saved: MutableList<UserProfile> = mutableListOf()

    /** The current profile, readable and writable without going through [save]. */
    var profile: UserProfile?
        get() = flow.value
        set(value) {
            flow.value = value
        }

    override fun observeProfile(): Flow<UserProfile?> = flow

    override suspend fun getProfile(): UserProfile? = flow.value

    override suspend fun save(profile: UserProfile) {
        saved += profile
        flow.value = profile
    }

    override suspend fun deleteAll() {
        flow.value = null
    }
}

/**
 * A complete profile, with every field overridable.
 *
 * The six fakes carried three different sample profiles between them —
 * bodyweight-only at three days, barbell at four, a mixed set at four — and
 * none of the differences were load-bearing except to the test that wrote them.
 * Named arguments say which difference a test actually depends on, and the
 * defaults say what the rest of it is not about.
 */
fun sampleProfile(
    id: String = "user-1",
    goal: TrainingGoal = TrainingGoal.STRENGTH,
    experience: ExperienceLevel = ExperienceLevel.INTERMEDIATE,
    trainingDaysPerWeek: Int = 4,
    sessionLengthMinutes: Int = 45,
    availableEquipment: Set<Equipment> = setOf(Equipment.BODY_WEIGHT, Equipment.BARBELL),
    preferredMuscles: Set<Muscle> = emptySet(),
    exclusions: Set<MovementExclusion> = emptySet(),
): UserProfile = UserProfile(
    id = id,
    goal = goal,
    experience = experience,
    trainingDaysPerWeek = trainingDaysPerWeek,
    sessionLengthMs = sessionLengthMinutes * 60_000L,
    availableEquipment = availableEquipment,
    preferredMuscles = preferredMuscles,
    exclusions = exclusions,
)
