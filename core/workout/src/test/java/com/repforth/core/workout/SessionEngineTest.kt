package com.repforth.core.workout

import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Every arrow in §10's diagram, plus the three things a workout timer gets wrong
 * in the field: a pause that eats the rest it suspended, a countdown that follows
 * the wall clock, and a duplicated command that applies twice.
 *
 * No device and no waiting — the engine reads an injected clock, so a ninety
 * second rest takes a microsecond to test.
 */
class SessionEngineTest {

    private lateinit var time: FakeTimeSource
    private lateinit var engine: SessionEngine

    private val restMs = 90_000L

    @Before
    fun setUp() {
        time = FakeTimeSource()
        engine = SessionEngine(time)
    }

    private fun template(exercises: Int = 2, sets: Int = 2) = WorkoutTemplate(
        id = "plan",
        name = "Test",
        source = PlanSource.MANUAL,
        exercises = (0 until exercises).map { index ->
            PlannedExercise(
                id = "pe$index",
                exerciseId = ExerciseId("000${index + 1}"),
                position = index,
                target = ExerciseTarget.Reps(sets = sets, reps = 10),
                restMs = restMs,
            )
        },
    )

    private var counter = 0
    private fun id() = "cmd-${counter++}"

    private fun SessionSnapshot.applying(command: SessionCommand): SessionSnapshot {
        val result = engine.apply(this, command)
        assertTrue("expected $command to apply, got $result", result is CommandResult.Applied)
        return result.state
    }

    private fun started() = engine.start("s1", template()).applying(SessionCommand.Begin(id()))

    // ── The happy path, arrow by arrow ───────────────────────────────────────

    @Test
    fun `a new session starts in preparing`() {
        val session = engine.start("s1", template())
        assertEquals(SessionPhase.PREPARING, session.phase)
        assertEquals(2, session.exercises.size)
        assertEquals(0, session.revision)
    }

    @Test
    fun `preparing to active on begin`() {
        assertEquals(SessionPhase.ACTIVE, started().phase)
    }

    @Test
    fun `completing a set moves to resting and starts the timer`() {
        val resting = started().applying(SessionCommand.CompleteSet(id(), reps = 10))
        assertEquals(SessionPhase.RESTING, resting.phase)
        assertEquals(restMs, resting.restRemaining(time.elapsedRealtime()))
    }

    @Test
    fun `rest elapsing returns to active on the next set`() {
        val session = started()
            .applying(SessionCommand.CompleteSet(id(), reps = 10))
            .applying(SessionCommand.RestElapsed(id()))
        assertEquals(SessionPhase.ACTIVE, session.phase)
        assertEquals(0, session.currentExerciseIndex)
        assertEquals(1, session.currentSetIndex)
    }

    @Test
    fun `finishing an exercise moves to the next one`() {
        var session = started()
        repeat(2) {
            session = session.applying(SessionCommand.CompleteSet(id(), reps = 10))
                .applying(SessionCommand.RestElapsed(id()))
        }
        assertEquals(1, session.currentExerciseIndex)
        assertEquals(0, session.currentSetIndex)
    }

    @Test
    fun `the final set goes straight to completing, not through a rest`() {
        // §10 routes the last set to COMPLETING. Resting first would leave the
        // user waiting out ninety seconds after their workout ended.
        var session = started()
        repeat(4) { index ->
            session = session.applying(SessionCommand.CompleteSet(id(), reps = 10))
            if (index < 3) session = session.applying(SessionCommand.RestElapsed(id()))
        }
        assertEquals(SessionPhase.COMPLETING, session.phase)
    }

    @Test
    fun `completing ends the session and stamps the end time`() {
        var session = started()
        repeat(4) { index ->
            session = session.applying(SessionCommand.CompleteSet(id(), reps = 10))
            if (index < 3) session = session.applying(SessionCommand.RestElapsed(id()))
        }
        val done = session.applying(SessionCommand.Finish(id()))
        assertEquals(SessionPhase.COMPLETED, done.phase)
        assertNotNull(done.endedAt)
        assertTrue(done.phase.isTerminal)
    }

    // ── Pause, and the bug it usually has ────────────────────────────────────

    @Test
    fun `pausing during rest remembers to return to rest`() {
        val paused = started()
            .applying(SessionCommand.CompleteSet(id(), reps = 10))
            .applying(SessionCommand.Pause(id()))
        assertEquals(SessionPhase.PAUSED, paused.phase)
        assertEquals(SessionPhase.RESTING, paused.phaseBeforePause)
    }

    @Test
    fun `pausing during a set returns to the set, not to a rest`() {
        val paused = started().applying(SessionCommand.Pause(id()))
        val resumed = paused.applying(SessionCommand.Resume(id()))
        assertEquals(SessionPhase.ACTIVE, resumed.phase)
        assertNull(resumed.restEndsAtElapsed)
    }

    @Test
    fun `a long pause does not eat the rest it suspended`() {
        // The bug this exists for: capturing the deadline instead of the
        // remaining time. A five minute pause would then consume the whole
        // ninety second rest and resume straight into the next set.
        var session = started().applying(SessionCommand.CompleteSet(id(), reps = 10))
        time.advance(30_000)
        session = session.applying(SessionCommand.Pause(id()))
        assertEquals(60_000L, session.restRemaining(time.elapsedRealtime()))

        time.advance(5 * 60_000)
        session = session.applying(SessionCommand.Resume(id()))
        assertEquals(SessionPhase.RESTING, session.phase)
        assertEquals(
            "the rest should resume with what was left, not what elapsed",
            60_000L,
            session.restRemaining(time.elapsedRealtime()),
        )
    }

    @Test
    fun `the countdown ignores the wall clock moving`() {
        // A timezone change or an NTP correction must not move a running rest.
        val session = started().applying(SessionCommand.CompleteSet(id(), reps = 10))
        val before = session.restRemaining(time.elapsedRealtime())
        time.skewWallClock(-3 * 60 * 60 * 1000L)
        assertEquals(before, session.restRemaining(time.elapsedRealtime()))
    }

    @Test
    fun `rest never counts below zero`() {
        val session = started().applying(SessionCommand.CompleteSet(id(), reps = 10))
        time.advance(restMs * 3)
        assertEquals(0L, session.restRemaining(time.elapsedRealtime()))
    }

    // ── Idempotency, for the watch in Phase 4 ────────────────────────────────

    @Test
    fun `a duplicated command returns the current state without applying twice`() {
        val command = SessionCommand.CompleteSet("dupe", reps = 10)
        val once = started().applying(command)
        val result = engine.apply(once, command)

        assertTrue(result is CommandResult.Unchanged)
        assertEquals(once.revision, result.state.revision)
        assertEquals(1, once.exercises.first().sets.size)
    }

    @Test
    fun `a command carrying a stale revision is ignored`() {
        val session = started().applying(SessionCommand.CompleteSet(id(), reps = 10))
        val stale = SessionCommand.SkipRest(id(), expectedRevision = 0)
        val result = engine.apply(session, stale)
        assertTrue(result is CommandResult.Unchanged)
        assertEquals(SessionPhase.RESTING, result.state.phase)
    }

    @Test
    fun `a command carrying the current revision applies`() {
        val session = started()
        val result = engine.apply(
            session,
            SessionCommand.CompleteSet(id(), reps = 10, expectedRevision = session.revision),
        )
        assertTrue(result is CommandResult.Applied)
    }

    @Test
    fun `revision advances on every applied command and not otherwise`() {
        val session = started()
        val after = session.applying(SessionCommand.CompleteSet(id(), reps = 10))
        assertEquals(session.revision + 1, after.revision)

        val rejected = engine.apply(after, SessionCommand.Resume(id()))
        assertTrue(rejected is CommandResult.Rejected)
        assertEquals(after.revision, rejected.state.revision)
    }

    // ── Abandon and illegal transitions ──────────────────────────────────────

    @Test
    fun `abandoning keeps the sets already recorded`() {
        val session = started()
            .applying(SessionCommand.CompleteSet(id(), reps = 8, weightKg = 40.0))
            .applying(SessionCommand.Abandon(id()))
        assertEquals(SessionPhase.ABANDONED, session.phase)
        assertEquals(1, session.exercises.first().sets.size)
        assertEquals(8, session.exercises.first().sets.first().reps)
    }

    @Test
    fun `abandon is reachable from active, resting and paused`() {
        listOf(
            started(),
            started().applying(SessionCommand.CompleteSet(id(), reps = 10)),
            started().applying(SessionCommand.Pause(id())),
        ).forEach { session ->
            val result = engine.apply(session, SessionCommand.Abandon(id()))
            assertTrue("could not abandon from ${session.phase}", result is CommandResult.Applied)
        }
    }

    @Test
    fun `a terminal session refuses every command`() {
        val done = started().applying(SessionCommand.Abandon(id()))
        listOf(
            SessionCommand.CompleteSet(id(), reps = 10),
            SessionCommand.Pause(id()),
            SessionCommand.Resume(id()),
            SessionCommand.Finish(id()),
        ).forEach { command ->
            assertTrue(
                "$command should be rejected on a terminal session",
                engine.apply(done, command) is CommandResult.Rejected,
            )
        }
    }

    @Test
    fun `resuming a session that is not paused is rejected`() {
        assertTrue(engine.apply(started(), SessionCommand.Resume(id())) is CommandResult.Rejected)
    }

    @Test
    fun `a session with no exercises cannot begin`() {
        val empty = engine.start("s", template().copy(exercises = emptyList()))
        assertTrue(engine.apply(empty, SessionCommand.Begin(id())) is CommandResult.Rejected)
    }

    // ── Skips ────────────────────────────────────────────────────────────────

    @Test
    fun `a skipped set is recorded, not omitted`() {
        // "Four of five" and "four" are different facts, and only the first
        // survives if skips are rows.
        val session = started().applying(SessionCommand.SkipSet(id()))
        val outcome = session.exercises.first().sets.single()
        assertTrue(outcome.skipped)
        assertNull("a skipped set has no reps", outcome.reps)
    }

    @Test
    fun `skipping to the next exercise abandons the sets left on this one`() {
        val session = started().applying(SessionCommand.NextExercise(id()))
        assertEquals(1, session.currentExerciseIndex)
        assertEquals(0, session.currentSetIndex)
        assertTrue(session.exercises.first().sets.isEmpty())
    }

    @Test
    fun `skipping past the last exercise completes the session`() {
        val session = started()
            .applying(SessionCommand.NextExercise(id()))
            .applying(SessionCommand.NextExercise(id()))
        assertEquals(SessionPhase.COMPLETING, session.phase)
    }

    // ── Restoring after the process dies ─────────────────────────────────────

    @Test
    fun `a restored rest resumes with the time that is genuinely left`() {
        // Monotonic time means nothing across a restart, so the deadline is
        // persisted as wall clock and converted back here. This is the whole
        // reason the app carries two clocks.
        val resting = started().applying(SessionCommand.CompleteSet(id(), reps = 10))
        val deadlineWallClock = time.now() + restMs

        time.advance(30_000)
        val reborn = FakeTimeSource(wallClock = time.now(), monotonic = 0)
        val restored = SessionEngine(reborn).restore(
            resting.copy(restEndsAtElapsed = null),
            deadlineWallClock,
        )
        assertEquals(60_000L, restored.restRemaining(reborn.elapsedRealtime()))
    }

    @Test
    fun `a rest that expired while the process was dead restores as finished`() {
        val resting = started().applying(SessionCommand.CompleteSet(id(), reps = 10))
        val deadlineWallClock = time.now() + restMs

        time.advance(restMs * 2)
        val reborn = FakeTimeSource(wallClock = time.now(), monotonic = 0)
        val restored = SessionEngine(reborn).restore(resting, deadlineWallClock)
        assertEquals(
            "an overdue rest is finished, never negative",
            0L,
            restored.restRemaining(reborn.elapsedRealtime()),
        )
    }

    @Test
    fun `restoring a session that was not resting changes nothing`() {
        val active = started()
        assertEquals(active, engine.restore(active, deadlineAtWallClock = null))
    }
}
