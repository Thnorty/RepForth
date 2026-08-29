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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessions = FakeSessions()
        templates = FakeTemplates()
        profiles = FakeProfiles()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TodayViewModel(
        sessions, templates, profiles, FakeTimeSource(), ZoneId.of("Europe/Istanbul"),
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
