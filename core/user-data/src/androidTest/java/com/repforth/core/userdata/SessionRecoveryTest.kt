package com.repforth.core.userdata

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.database.RepForthDatabase
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.workout.CommandResult
import com.repforth.core.workout.SessionCommand
import com.repforth.core.workout.SessionEngine
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a workout in progress survives, across a real database.
 *
 * The unit that was edited is [RoomSessionRepository], and testing it against a
 * fake row store is the easy half — a fake hands back the entity object it was
 * given, so a field the repository never writes to a column still comes back
 * intact. That is precisely the shape of the paused-rest defect below, and a
 * fake DAO cannot see it. Room in memory is real Room: real DDL, real columns,
 * the real `@Relation` queries, and a value that reaches no column is gone.
 *
 * Each test round-trips through a **second repository instance**, because that
 * is what a process restart actually is. Nothing carries over in memory; the
 * only thing crossing the boundary is the row.
 *
 * Two of these were red before the fix that added the stored cursor and the
 * paused-rest remainder — `restoring_a_rest_then_ending_it_enters_the_next_set`
 * entered set 3 of 3 after set 1, and
 * `restoring_a_paused_rest_resumes_with_the_time_that_was_left` came back with
 * no deadline at all, so the rest could never end. A third,
 * `restoring_after_skipping_an_exercise_stays_on_the_new_one`, walked straight
 * back into the exercise the user had just left.
 *
 * ```
 * ./gradlew :core:user-data:pixel6Api34DebugAndroidTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SessionRecoveryTest {

    private lateinit var database: RepForthDatabase
    private lateinit var time: FakeTimeSource
    private lateinit var engine: SessionEngine

    private val commandIds = AtomicInteger()

    @Before
    fun setUp() {
        // In-memory rather than a file: the schema, the DDL and the queries are
        // the real ones, and only the storage is transient. A file would also
        // work and would leave a database behind between runs.
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RepForthDatabase::class.java,
        ).build()
        time = FakeTimeSource()
        engine = SessionEngine(time)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** A fresh repository over the same rows: what the next process sees. */
    private fun repository() = RoomSessionRepository(database.sessionDao(), time)

    private fun id() = "cmd-${commandIds.incrementAndGet()}"

    private fun template(exercises: Int = 2, sets: Int = 3) = WorkoutTemplate(
        id = "plan",
        name = "Recovery",
        source = PlanSource.MANUAL,
        exercises = (0 until exercises).map { position ->
            PlannedExercise(
                id = "planned-$position",
                exerciseId = ExerciseId("exercise-$position"),
                position = position,
                target = ExerciseTarget.Reps(sets = sets, reps = 10),
                restMs = REST_MS,
            )
        },
    )

    private fun begun(exercises: Int = 2, sets: Int = 3): SessionSnapshot =
        engine.start("session", template(exercises, sets)) + SessionCommand.Begin(id())

    /** Applies a command, failing the test if the engine refused it. */
    private operator fun SessionSnapshot.plus(command: SessionCommand): SessionSnapshot {
        val result = engine.apply(this, command)
        assertTrue("The engine refused $command: $result", result is CommandResult.Applied)
        return result.state
    }

    private suspend fun restart(snapshot: SessionSnapshot): SessionSnapshot {
        repository().persist(snapshot)
        return requireNotNull(repository().restoreActive()) {
            "A session that has not finished must be found again"
        }
    }

    @Test
    fun restoring_mid_exercise_keeps_the_set_the_user_is_on() = runTest {
        // Set 1 done, rest skipped: the user is standing at set 2 of 3.
        val onSetTwo = begun() + SessionCommand.CompleteSet(id()) + SessionCommand.SkipRest(id())
        assertEquals(1, onSetTwo.currentSetIndex)

        assertEquals(1, restart(onSetTwo).currentSetIndex)
    }

    @Test
    fun restoring_a_rest_then_ending_it_enters_the_next_set() = runTest {
        // The defect this test was written for. During a rest the engine's
        // cursor still names the set just finished — it advances when the rest
        // ends, not when the set is logged. Counting the stored set records read
        // that as the next set, so ending the restored rest advanced a second
        // time and set 2 of 3 was never offered.
        val resting = begun() + SessionCommand.CompleteSet(id())
        assertEquals(SessionPhase.RESTING, resting.phase)

        val expected = resting + SessionCommand.SkipRest(id())
        val restored = restart(resting) + SessionCommand.SkipRest(id())

        assertEquals("The set after a restored rest", expected.currentSetIndex, restored.currentSetIndex)
        assertEquals(1, restored.currentSetIndex)
    }

    @Test
    fun restoring_a_rest_after_the_last_set_enters_the_next_exercise() = runTest {
        var state = begun(exercises = 2, sets = 2) + SessionCommand.CompleteSet(id())
        state += SessionCommand.SkipRest(id())
        // Second and final set of exercise one; the rest after it leads to
        // exercise two rather than to a third set.
        val resting = state + SessionCommand.CompleteSet(id())
        assertEquals(SessionPhase.RESTING, resting.phase)

        val restored = restart(resting) + SessionCommand.SkipRest(id())

        assertEquals(1, restored.currentExerciseIndex)
        assertEquals(0, restored.currentSetIndex)
    }

    @Test
    fun restoring_after_a_skipped_set_keeps_the_skip() = runTest {
        // A skip is a record, not a gap, so this one was already right. It is
        // here because the stored cursor must not regress it.
        val afterSkip = begun() + SessionCommand.SkipSet(id()) + SessionCommand.SkipRest(id())

        val restored = restart(afterSkip)

        assertEquals(1, restored.currentSetIndex)
        assertEquals(1, restored.exercises[0].sets.size)
        assertTrue("A skipped set is stored as skipped", restored.exercises[0].sets[0].skipped)
    }

    /**
     * The cursor must not walk back to an exercise whose sets were all skipped.
     *
     * This used to travel by `NextExercise`, which recorded nothing at all — so
     * the old derivation looked for the first exercise still owed sets and found
     * the one the user had just left. That command was removed on 2026-09-10 and
     * the route here is skipping every set instead, which is now the only way to
     * leave an exercise.
     *
     * The derivation is still the thing under test and the risk is the same in
     * kind: a skipped set is a row, but it is a row with no reps and no weight,
     * and anything counting *performed* work would still read this exercise as
     * unfinished.
     */
    @Test
    fun restoring_after_skipping_every_set_stays_on_the_new_exercise() = runTest {
        var moved = begun(exercises = 2, sets = 2)
        repeat(2) {
            moved += SessionCommand.SkipSet(id())
            moved += SessionCommand.SkipRest(id())
        }
        assertEquals(1, moved.currentExerciseIndex)

        val restored = restart(moved)

        assertEquals("A skipped exercise stays behind", 1, restored.currentExerciseIndex)
        assertEquals(0, restored.currentSetIndex)
    }

    @Test
    fun restoring_a_paused_rest_resumes_with_the_time_that_was_left() = runTest {
        val resting = begun() + SessionCommand.CompleteSet(id())
        time.advance(10_000L)
        val paused = resting + SessionCommand.Pause(id())
        assertEquals(REST_MS - 10_000L, paused.restRemainingMs)

        val restored = restart(paused)
        assertEquals(
            "The remainder is what a pause leaves behind",
            REST_MS - 10_000L,
            restored.restRemainingMs,
        )

        // A pause has no length, so the clock moving while paused must not come
        // out of the rest. This is why the remainder is a duration and not a
        // deadline.
        time.advance(5 * 60_000L)
        val resumed = restored + SessionCommand.Resume(id())

        assertEquals(SessionPhase.RESTING, resumed.phase)
        assertEquals(
            "Resuming a restored pause owes the full remainder",
            REST_MS - 10_000L,
            resumed.restRemaining(time.elapsedRealtime()),
        )
    }

    @Test
    fun restoring_a_running_rest_keeps_the_time_that_is_left() = runTest {
        // The wall-clock anchor, which already worked; it is asserted here so
        // the two rest paths are covered by the same class.
        val resting = begun() + SessionCommand.CompleteSet(id())
        time.advance(20_000L)

        val restored = restart(resting)

        assertEquals(REST_MS - 20_000L, restored.restRemaining(time.elapsedRealtime()))
    }

    @Test
    fun restoring_a_paused_set_returns_to_the_set_not_to_a_rest() = runTest {
        val paused = begun() + SessionCommand.Pause(id())

        val resumed = restart(paused) + SessionCommand.Resume(id())

        assertEquals(SessionPhase.ACTIVE, resumed.phase)
        assertEquals(0, resumed.currentSetIndex)
        assertEquals(null, resumed.restEndsAtElapsed)
    }

    @Test
    fun a_finished_session_is_no_longer_the_active_one() = runTest {
        var state = begun(exercises = 1, sets = 1) + SessionCommand.CompleteSet(id())
        assertEquals(SessionPhase.COMPLETING, state.phase)
        state += SessionCommand.Finish(id())
        repository().persist(state)

        assertEquals(null, repository().restoreActive())
    }

    // ---- Timed sets, which carry a second clock ----

    private fun timedTemplate(sets: Int = 2) = WorkoutTemplate(
        id = "plan",
        name = "Core",
        source = PlanSource.MANUAL,
        exercises = listOf(
            PlannedExercise(
                id = "planned-0",
                exerciseId = ExerciseId("exercise-0"),
                position = 0,
                target = ExerciseTarget.Duration(sets = sets, durationMs = HOLD_MS),
                restMs = REST_MS,
            ),
        ),
    )

    @Test
    fun restoring_a_running_timed_set_owes_what_is_left_of_it() = runTest {
        val running = engine.start("session", timedTemplate()) + SessionCommand.Begin(id())
        assertEquals(HOLD_MS, running.setRemaining(time.elapsedRealtime()))
        time.advance(20_000L)

        val restored = restart(running)

        assertEquals(
            "A plank interrupted by a process death owes the seconds it had left",
            HOLD_MS - 20_000L,
            restored.setRemaining(time.elapsedRealtime()),
        )
    }

    @Test
    fun restoring_a_paused_timed_set_resumes_with_the_time_that_was_left() = runTest {
        val running = engine.start("session", timedTemplate()) + SessionCommand.Begin(id())
        time.advance(20_000L)
        val paused = running + SessionCommand.Pause(id())

        val restored = restart(paused)
        assertEquals(HOLD_MS - 20_000L, restored.setRemainingMs)

        // The clock moving while paused must not come out of the set, which is
        // why the remainder is a duration and not a deadline.
        time.advance(10 * 60_000L)
        val resumed = restored + SessionCommand.Resume(id())

        assertEquals(SessionPhase.ACTIVE, resumed.phase)
        assertEquals(
            HOLD_MS - 20_000L,
            resumed.setRemaining(time.elapsedRealtime()),
        )
    }

    @Test
    fun a_restored_rest_after_a_timed_set_counts_the_rest_and_not_the_set() = runTest {
        // Both deadlines live on one row. Persisting one into the other's column
        // would be invisible until a restored rest counted down a plank.
        val resting = engine.start("session", timedTemplate()) +
            SessionCommand.Begin(id()) + SessionCommand.SetElapsed(id())
        assertEquals(SessionPhase.RESTING, resting.phase)

        val restored = restart(resting)

        assertEquals(REST_MS, restored.restRemaining(time.elapsedRealtime()))
        assertEquals(null, restored.setRemaining(time.elapsedRealtime()))
    }

    private companion object {
        const val REST_MS = 60_000L
        const val HOLD_MS = 45_000L
    }
}
