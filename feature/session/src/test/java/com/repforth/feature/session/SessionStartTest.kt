package com.repforth.feature.session

import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.workout.SessionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Starting a plan starts *that* plan, or says why it cannot.
 *
 * This exists because it used to do neither. `start` returned whatever session
 * was already running, whichever plan had been asked for — so tapping a plan
 * silently resumed a different one, often a workout left unfinished days
 * earlier, since an active session never expires on its own. The screen then
 * showed a workout the user had not chosen, part-way through, with no
 * explanation. Reported from a real device.
 */
class SessionStartTest {

    @Test
    fun `starting a plan when nothing is running begins it`() = runTest {
        val outcome = controller().start(PUSH)

        assertTrue(outcome is StartOutcome.Started)
        assertEquals(PUSH, (outcome as StartOutcome.Started).snapshot.templateId)
    }

    /** Tapping the plan that is already going is not a conflict; it is what was meant. */
    @Test
    fun `starting the plan that is already running resumes it`() = runTest {
        val controller = controller()
        controller.start(PUSH)

        val outcome = controller.start(PUSH)

        assertTrue(outcome is StartOutcome.Resumed)
        assertEquals(PUSH, (outcome as StartOutcome.Resumed).snapshot.templateId)
    }

    /**
     * The bug, as an assertion.
     *
     * A different plan must not be silently substituted, and the running
     * workout must not be silently destroyed either. Both are refusals to
     * guess.
     */
    @Test
    fun `starting a different plan is blocked and leaves the running one alone`() = runTest {
        val controller = controller()
        controller.start(PUSH)

        val outcome = controller.start(PULL)

        assertTrue("Expected Blocked, got $outcome", outcome is StartOutcome.Blocked)
        assertEquals(PUSH, (outcome as StartOutcome.Blocked).running.templateId)
        assertEquals(PUSH, controller.state.value?.templateId)
    }

    @Test
    fun `discarding the running workout starts the requested one`() = runTest {
        val controller = controller()
        controller.start(PUSH)

        val outcome = controller.abandonAndStart(PULL)

        assertTrue("Expected Started, got $outcome", outcome is StartOutcome.Started)
        assertEquals(PULL, (outcome as StartOutcome.Started).snapshot.templateId)
        assertEquals(PULL, controller.state.value?.templateId)
    }

    @Test
    fun `starting a plan that no longer exists reports so rather than crashing`() = runTest {
        assertEquals(StartOutcome.NoSuchPlan, controller().start("deleted"))
    }

    /**
     * A cold process must not start a second workout on top of the first.
     *
     * `start` used to read only the in-memory session, which is null until
     * something calls `restore`. So whether tapping a plan noticed the workout
     * already in the database came down to which coroutine reached the
     * controller first — and losing that race meant two live sessions, which is
     * worse than the bug this class was written for.
     */
    @Test
    fun `a workout in the database blocks a different plan before anything has restored it`() = runTest {
        val sessions = StartFakeSessions()
        SessionController(sessions, StartFakeTemplates(), FakeTimeSource()).start(PUSH)

        // A new controller over the same database, as after the process died.
        // Nothing has called restore() on it.
        val cold = SessionController(sessions, StartFakeTemplates(), FakeTimeSource())
        val outcome = cold.start(PULL)

        assertTrue("Expected Blocked, got $outcome", outcome is StartOutcome.Blocked)
        assertEquals(PUSH, (outcome as StartOutcome.Blocked).running.templateId)
    }

    private fun controller() = SessionController(
        StartFakeSessions(),
        StartFakeTemplates(),
        FakeTimeSource(),
    )

    private companion object {
        const val PUSH = "push-day"
        const val PULL = "pull-day"
    }
}

/** Two plans with genuinely different ids, which is the whole point here. */
internal class StartFakeTemplates : TemplateRepository {
    override fun observeAll(): Flow<List<WorkoutTemplate>> = emptyFlow()

    override suspend fun find(id: String): WorkoutTemplate? = when (id) {
        "push-day", "pull-day" -> WorkoutTemplate(
            id = id,
            name = id,
            source = PlanSource.MANUAL,
            exercises = listOf(
                PlannedExercise(
                    id = "$id-0",
                    exerciseId = ExerciseId("ex-0"),
                    position = 0,
                    target = ExerciseTarget.Reps(sets = 3, reps = 10),
                    restMs = 60_000L,
                ),
            ),
        )

        else -> null
    }

    override suspend fun save(template: WorkoutTemplate) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun deleteAll() = Unit
}

internal class StartFakeSessions : SessionRepository {
    private val persisted = mutableListOf<SessionSnapshot>()

    override fun observeActive(): Flow<SessionSnapshot?> = emptyFlow()
    override suspend fun restoreActive(): SessionSnapshot? = persisted.lastOrNull()
    override fun observeFinished(): Flow<List<SessionSnapshot>> = emptyFlow()
    override suspend fun persist(snapshot: SessionSnapshot) {
        persisted.removeAll { it.sessionId == snapshot.sessionId }
        persisted += snapshot
    }
    override suspend fun deleteAll() = persisted.clear()
}
