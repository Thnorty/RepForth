package com.repforth.feature.home

import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.WeekDay
import com.repforth.core.userdata.WeekRepository

/**
 * What Today decides to show.
 *
 * The ordering is the whole feature: a workout in progress outranks a
 * suggestion, and a suggestion outranks an empty state. Getting that wrong is
 * invisible in a screenshot and obvious to someone who opened the app mid-set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessions: FakeSessions
    private lateinit var templates: FakeTemplates
    private lateinit var profiles: FakeProfiles
    private lateinit var weeks: FakeWeeks

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessions = FakeSessions()
        templates = FakeTemplates()
        profiles = FakeProfiles()
        weeks = FakeWeeks()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TodayViewModel(
        sessions, templates, profiles, weeks, FakeTimeSource(), ZoneId.of("Europe/Istanbul"),
    )

    private suspend fun state() = viewModel().uiState.first { !it.loading }

    @Test
    fun `a fresh install has nothing to suggest`() = runTest(dispatcher) {
        val state = state()

        assertNull(state.next)
        assertNull(state.active)
        assertTrue("The empty state is what the screen shows", !state.hasPlans)
    }

    @Test
    fun `a saved plan is offered`() = runTest(dispatcher) {
        templates.emit(listOf(plan("a", "Push day")))

        val state = state()

        assertEquals("Push day", state.next?.name)
        assertNull("Never performed", state.nextLastPerformedAt)
    }

    /**
     * Someone who opens the app mid-workout is not looking for a suggestion.
     */
    @Test
    fun `a workout in progress is reported alongside the suggestion`() = runTest(dispatcher) {
        templates.emit(listOf(plan("a", "Push day")))
        sessions.active.value = session("live", "a", startedAt = 5_000, phase = SessionPhase.ACTIVE)

        val state = state()

        assertEquals("live", state.active?.sessionId)
    }

    @Test
    fun `the stalest plan is the one offered, with when it was last done`() =
        runTest(dispatcher) {
            templates.emit(listOf(plan("a", "Push"), plan("b", "Pull")))
            sessions.finished.value = listOf(
                session("s1", "a", startedAt = 5_000),
                session("s2", "b", startedAt = 1_000),
            )

            val state = state()

            assertEquals("Pull", state.next?.name)
            assertEquals(1_000L, state.nextLastPerformedAt)
        }

    @Test
    fun `the training goal from onboarding reaches the week card`() = runTest(dispatcher) {
        profiles.profile.value = profile(daysPerWeek = 4)

        val state = state()

        assertEquals(4, state.trainingDaysPerWeek)
    }

    @Test
    fun `history counts towards the week`() = runTest(dispatcher) {
        // FakeTimeSource starts at 0, so a session at 0 is in the current week.
        sessions.finished.value = listOf(session("s1", "a", startedAt = 0))

        val state = state()

        assertEquals(1, state.progress.workouts)
    }

    private fun plan(id: String, name: String) = WorkoutTemplate(
        id = id,
        name = name,
        source = PlanSource.MANUAL,
        exercises = listOf(
            PlannedExercise("row-$id", ExerciseId("ex"), 0, ExerciseTarget.Reps(3, 10), 60_000),
        ),
    )

    @Test
    fun `an active weekly plan takes priority for recommendation`() = runTest(dispatcher) {
        val day0 = WeekDay(0, "Push", workout = plan("d0", "Push Day"))
        val day1 = WeekDay(1, "Pull", workout = plan("d1", "Pull Day"))
        val week = TrainingWeek(
            id = "w1",
            name = "PPL Week",
            source = PlanSource.AI,
            active = true,
            days = listOf(day0, day1),
        )
        templates.emit(listOf(plan("standalone", "Standalone Plan")))
        weeks.active.value = week

        val state = state()

        assertEquals("Push Day", state.next?.name)
    }

    /**
     * The recommendation has to say it is a day of a week.
     *
     * Today rendered it as an ordinary plan card with nothing naming the week or
     * the position, on the screen whose whole purpose is following one.
     */
    @Test
    fun `a day of the active week says which week and which day`() = runTest(dispatcher) {
        weeks.active.value = TrainingWeek(
            id = "w1",
            name = "PPL Week",
            source = PlanSource.AI,
            active = true,
            days = listOf(
                WeekDay(0, "Push", workout = plan("d0", "Push Day")),
                WeekDay(1, "Pull", workout = plan("d1", "Pull Day")),
                WeekDay(2, "Legs", workout = plan("d2", "Leg Day")),
            ),
        )

        val state = state()

        assertEquals("PPL Week", state.activeWeekName)
        assertEquals(0, state.nextWeekDayPosition)
        assertEquals(3, state.activeWeekDayCount)
    }

    /** A standalone plan is not a day of anything, and must not be labelled one. */
    @Test
    fun `a standalone recommendation carries no week label`() = runTest(dispatcher) {
        templates.emit(listOf(plan("standalone", "Standalone Plan")))

        val state = state()

        assertEquals("Standalone Plan", state.next?.name)
        assertNull(state.activeWeekName)
        assertNull(state.nextWeekDayPosition)
    }

    /**
     * The week card counted against the profile whatever week was running.
     *
     * A seven-day week read "0 of 3 days" — a target with nothing to do with the
     * week being followed.
     */
    @Test
    fun `the week card counts against the active week, not the profile`() =
        runTest(dispatcher) {
            profiles.profile.value = profile(daysPerWeek = 3)
            weeks.active.value = TrainingWeek(
                id = "w1",
                name = "Seven",
                source = PlanSource.AI,
                active = true,
                days = (0 until 7).map { WeekDay(it, "Day $it", workout = plan("d$it", "Day $it")) },
            )

            assertEquals(7, state().weeklyTarget)
        }

    /** With no week running, the profile is still the answer. */
    @Test
    fun `the week card falls back to the profile when no week is active`() =
        runTest(dispatcher) {
            profiles.profile.value = profile(daysPerWeek = 4)

            assertEquals(4, state().weeklyTarget)
        }

    private fun session(
        id: String,
        templateId: String?,
        startedAt: Long,
        phase: SessionPhase = SessionPhase.COMPLETED,
    ) = SessionSnapshot(
        sessionId = id,
        templateId = templateId,
        phase = phase,
        exercises = emptyList(),
        startedAt = startedAt,
        endedAt = startedAt + 1,
    )

    private fun profile(daysPerWeek: Int) = UserProfile(
        id = "p",
        goal = TrainingGoal.STRENGTH,
        experience = ExperienceLevel.BEGINNER,
        trainingDaysPerWeek = daysPerWeek,
        sessionLengthMs = 45 * 60_000L,
        availableEquipment = setOf(Equipment.BODY_WEIGHT),
        preferredMuscles = emptySet(),
        exclusions = emptySet(),
    )
}

private class FakeWeeks : WeekRepository {
    val active = MutableStateFlow<TrainingWeek?>(null)
    val all = MutableStateFlow<List<TrainingWeek>>(emptyList())

    override fun observeAll(): Flow<List<TrainingWeek>> = all
    override fun observeActive(): Flow<TrainingWeek?> = active
    override suspend fun find(id: String): TrainingWeek? = all.value.firstOrNull { it.id == id }
    override suspend fun save(week: TrainingWeek) = Unit
    override suspend fun setActive(id: String) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun deleteAll() = Unit
}

private class FakeSessions : SessionRepository {
    val active = MutableStateFlow<SessionSnapshot?>(null)
    val finished = MutableStateFlow<List<SessionSnapshot>>(emptyList())

    override fun observeActive(): Flow<SessionSnapshot?> = active

    override suspend fun restoreActive(): SessionSnapshot? = active.value

    override fun observeFinished(): Flow<List<SessionSnapshot>> = finished

    override suspend fun persist(snapshot: SessionSnapshot) = Unit

    override suspend fun deleteAll() = Unit
}

private class FakeTemplates : TemplateRepository {
    private val all = MutableStateFlow<List<WorkoutTemplate>>(emptyList())

    fun emit(list: List<WorkoutTemplate>) {
        all.value = list
    }

    override fun observeAll(): Flow<List<WorkoutTemplate>> = all

    override suspend fun find(id: String): WorkoutTemplate? = all.value.firstOrNull { it.id == id }

    override suspend fun save(template: WorkoutTemplate) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun deleteAll() = Unit
}

private class FakeProfiles : ProfileRepository {
    val profile = MutableStateFlow<UserProfile?>(null)

    override fun observeProfile(): Flow<UserProfile?> = profile

    override suspend fun getProfile(): UserProfile? = profile.value

    override suspend fun save(profile: UserProfile) = Unit

    override suspend fun deleteAll() = Unit
}
