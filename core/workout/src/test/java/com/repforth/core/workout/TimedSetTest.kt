package com.repforth.core.workout

import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timed sets: §3's timed intervals, ended by a clock rather than by a tap.
 *
 * Two rules decided by the owner shape all of this. **The clock completes the
 * set** — there is no "Log set" for timed work, and [SessionCommand.CompleteSet]
 * is refused while one is running. **Stopping early is a skip**, not a set with a
 * shorter duration; a plank abandoned at forty seconds is not forty seconds of
 * work in the history, it is a set nobody finished.
 *
 * Together they make the recorded duration true. It was already the prescribed
 * duration — the screen passed the target straight back — and that was a lie only
 * because a human could tap early. With the clock as the only way to finish one,
 * "recorded sixty seconds" is sixty seconds by construction.
 *
 * **The timer starts on arrival**, which is the other decision and the reason
 * these tests are mostly about arriving. Four separate paths reach an `ACTIVE`
 * state, and one that forgot to arm would leave a set that never counts — and
 * therefore, with `CompleteSet` refused, never ends at all.
 */
class TimedSetTest {

    private val time = FakeTimeSource()
    private val engine = SessionEngine(time)
    private val ids = AtomicInteger()

    private fun id() = "cmd-${ids.incrementAndGet()}"

    /** A plan whose exercises are timed unless said otherwise. */
    private fun template(
        exercises: Int = 2,
        sets: Int = 2,
        restMs: Long = REST_MS,
        timed: Boolean = true,
    ) = WorkoutTemplate(
        id = "plan",
        name = "Core",
        source = PlanSource.MANUAL,
        exercises = (0 until exercises).map { position ->
            PlannedExercise(
                id = "planned-$position",
                exerciseId = ExerciseId("exercise-$position"),
                position = position,
                target = if (timed) {
                    ExerciseTarget.Duration(sets = sets, durationMs = HOLD_MS)
                } else {
                    ExerciseTarget.Reps(sets = sets, reps = 10)
                },
                restMs = restMs,
            )
        },
    )

    private fun begun(
        exercises: Int = 2,
        sets: Int = 2,
        restMs: Long = REST_MS,
        timed: Boolean = true,
    ) = engine.start("session", template(exercises, sets, restMs, timed)) +
        SessionCommand.Begin(id())

    private operator fun SessionSnapshot.plus(command: SessionCommand): SessionSnapshot {
        val result = engine.apply(this, command)
        assertTrue("The engine refused $command: $result", result is CommandResult.Applied)
        return result.state
    }

    private fun SessionSnapshot.remaining() = setRemaining(time.elapsedRealtime())

    // ---- Starting on arrival: all four ways in ----

    @Test
    fun `beginning a workout starts the first timed set`() {
        val begun = begun()

        assertEquals("The clock runs from arrival, with nothing to press", HOLD_MS, begun.remaining())
    }

    @Test
    fun `the rest after a timed set starts the next one`() {
        var state = begun() + SessionCommand.SetElapsed(id())
        assertEquals(SessionPhase.RESTING, state.phase)
        assertNull("Nothing counts down on the set while resting", state.remaining())

        state += SessionCommand.SkipRest(id())

        assertEquals(1, state.currentSetIndex)
        assertEquals(HOLD_MS, state.remaining())
    }

    @Test
    fun `moving to the next exercise starts its timer`() {
        val moved = begun() + SessionCommand.NextExercise(id())

        assertEquals(1, moved.currentExerciseIndex)
        assertEquals(HOLD_MS, moved.remaining())
    }

    @Test
    fun `an exercise with no rest starts the next set immediately`() {
        // The path that skips RESTING entirely rather than flickering through it
        // for one frame. It advances through the same helper, so it has to arm
        // through the same helper.
        val state = begun(restMs = 0L) + SessionCommand.SetElapsed(id())

        assertEquals(SessionPhase.ACTIVE, state.phase)
        assertEquals(1, state.currentSetIndex)
        assertEquals(HOLD_MS, state.remaining())
    }

    @Test
    fun `an untimed exercise has no clock`() {
        val begun = begun(timed = false)

        assertNull(begun.remaining())
        assertNull(begun.setEndsAtElapsed)
    }

    /** A plan can mix the two, and arriving at each has to do the right thing. */
    @Test
    fun `arriving at an untimed exercise after a timed one starts no clock`() {
        val mixed = WorkoutTemplate(
            id = "plan",
            name = "Mixed",
            source = PlanSource.MANUAL,
            exercises = listOf(
                PlannedExercise(
                    "a", ExerciseId("timed"), 0,
                    ExerciseTarget.Duration(sets = 1, durationMs = HOLD_MS), REST_MS,
                ),
                PlannedExercise(
                    "b", ExerciseId("reps"), 1,
                    ExerciseTarget.Reps(sets = 1, reps = 10), REST_MS,
                ),
            ),
        )
        var state = engine.start("session", mixed) + SessionCommand.Begin(id())
        assertEquals(HOLD_MS, state.remaining())

        state = state + SessionCommand.SetElapsed(id()) + SessionCommand.SkipRest(id())

        assertEquals(1, state.currentExerciseIndex)
        assertNull("A rep exercise has no clock to run", state.remaining())
    }

    // ---- Only the clock completes it ----

    @Test
    fun `a timed set cannot be logged by hand`() {
        val result = engine.apply(begun(), SessionCommand.CompleteSet(id()))

        assertTrue("A timed set ends on its own clock", result is CommandResult.Rejected)
        assertTrue(
            (result as CommandResult.Rejected).reason.contains("clock"),
        )
    }

    /**
     * Refused in the engine and not merely hidden on the screen.
     *
     * The watch is a second sender. A rule enforced only where the button is
     * drawn is a rule the other sender does not have.
     */
    @Test
    fun `an untimed set is still logged by hand`() {
        val logged = begun(timed = false) + SessionCommand.CompleteSet(id(), reps = 8)

        assertEquals(8, logged.exercises[0].sets.single().reps)
    }

    @Test
    fun `the clock records the prescribed duration`() {
        val state = begun() + SessionCommand.SetElapsed(id())

        val outcome = state.exercises[0].sets.single()
        assertEquals("A completed timed set ran its full length", HOLD_MS, outcome.durationMs)
        assertEquals(false, outcome.skipped)
    }

    @Test
    fun `stopping early is a skip and not a shorter set`() {
        time.advance(40_000L)
        val state = begun() + SessionCommand.SkipSet(id())

        val outcome = state.exercises[0].sets.single()
        assertTrue("Stopping early records a skip", outcome.skipped)
        assertNull("A skip has no duration to claim", outcome.durationMs)
    }

    @Test
    fun `the clock cannot end a set that is not timed`() {
        val result = engine.apply(begun(timed = false), SessionCommand.SetElapsed(id()))

        assertTrue(result is CommandResult.Rejected)
    }

    @Test
    fun `the clock cannot end a set twice`() {
        // The screen and the service both tick, so both can arrive at zero. The
        // second has to be refused without a write rather than recording a
        // second set.
        val ended = begun() + SessionCommand.SetElapsed(id())

        val again = engine.apply(ended, SessionCommand.SetElapsed(id()))

        assertTrue("The second tick finds a rest, not a set", again is CommandResult.Rejected)
        assertEquals(1, ended.exercises[0].sets.size)
    }

    @Test
    fun `the last timed set of the last exercise finishes the workout`() {
        var state = begun(exercises = 1, sets = 1)

        state += SessionCommand.SetElapsed(id())

        assertEquals(SessionPhase.COMPLETING, state.phase)
        assertNull("A finished workout counts nothing down", state.remaining())
    }

    // ---- Pausing, which owes the seconds it took ----

    @Test
    fun `pausing a timed set keeps what was left of it`() {
        val running = begun()
        time.advance(20_000L)

        val paused = running + SessionCommand.Pause(id())

        assertEquals(HOLD_MS - 20_000L, paused.setRemainingMs)
        assertNull("A paused clock has no deadline", paused.setEndsAtElapsed)
        assertEquals(HOLD_MS - 20_000L, paused.remaining())
    }

    @Test
    fun `a pause costs the set nothing however long it lasts`() {
        val running = begun()
        time.advance(20_000L)
        val paused = running + SessionCommand.Pause(id())

        // Ten minutes on the phone; the plank still owes what it owed.
        time.advance(10 * 60_000L)
        val resumed = paused + SessionCommand.Resume(id())

        assertEquals(SessionPhase.ACTIVE, resumed.phase)
        assertEquals(HOLD_MS - 20_000L, resumed.remaining())
    }

    @Test
    fun `resuming a paused rest does not start a set clock`() {
        // Both remainders live on the same snapshot, and resume has to give back
        // the one that was paused rather than both.
        val resting = begun() + SessionCommand.SetElapsed(id())
        val paused = resting + SessionCommand.Pause(id())

        val resumed = paused + SessionCommand.Resume(id())

        assertEquals(SessionPhase.RESTING, resumed.phase)
        assertNotNull(resumed.restEndsAtElapsed)
        assertNull("A rest is not a set", resumed.setEndsAtElapsed)
    }

    @Test
    fun `abandoning stops the clock`() {
        val abandoned = begun() + SessionCommand.Abandon(id())

        assertNull(abandoned.setEndsAtElapsed)
        assertNull(abandoned.setRemainingMs)
    }

    // ---- Restoring after the process died ----

    @Test
    fun `a restored timed set owes what the wall clock says`() {
        val running = begun()
        val deadline = time.now() + HOLD_MS

        // A new process: the monotonic clock means nothing across it, so the
        // engine rebuilds the deadline from the wall-clock one.
        time.advance(20_000L)
        val restored = engine.restore(
            running.copy(setEndsAtElapsed = null),
            deadlineAtWallClock = null,
            setDeadlineAtWallClock = deadline,
        )

        assertEquals(HOLD_MS - 20_000L, restored.remaining())
    }

    /**
     * A deadline that passed while the process was dead comes back at zero, and
     * the next tick records the set.
     *
     * The wall clock genuinely reached the end of it, which is the owner's rule
     * for what counts as completed. The alternative — restarting the count —
     * would record a set that took twice as long as it claims.
     */
    @Test
    fun `a timed set whose deadline passed while dead comes back finished`() {
        val running = begun()
        val deadline = time.now() + HOLD_MS

        time.advance(HOLD_MS + 60_000L)
        val restored = engine.restore(
            running.copy(setEndsAtElapsed = null),
            deadlineAtWallClock = null,
            setDeadlineAtWallClock = deadline,
        )

        assertEquals("Clamped at zero, never negative", 0L, restored.remaining())
        val ended = restored + SessionCommand.SetElapsed(id())
        assertEquals(HOLD_MS, ended.exercises[0].sets.single().durationMs)
    }

    private companion object {
        const val HOLD_MS = 60_000L
        const val REST_MS = 30_000L
    }
}
