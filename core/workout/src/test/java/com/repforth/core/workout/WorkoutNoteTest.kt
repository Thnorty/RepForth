package com.repforth.core.workout

import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §3's workout note and perceived effort: written once, as the workout closes.
 *
 * It rides on `Finish` rather than having a command of its own, because a note
 * is typed a character at a time and a command per keystroke would be a
 * revision, a database write and a watch publish per letter.
 *
 * The interesting assertions are all about *nothing* — what happens when the
 * user writes no note, or writes only spaces, or never gets to the end of the
 * workout. Storing an empty string for any of those puts a blank line in the
 * history that reads as a defect rather than as silence, and a middling 5 for an
 * effort nobody gave would be worse: a number that looks like data.
 */
class WorkoutNoteTest {

    private val time = FakeTimeSource()
    private val engine = SessionEngine(time)
    private val ids = AtomicInteger()

    private fun id() = "cmd-${ids.incrementAndGet()}"

    @Test
    fun `a note written at the end is kept`() {
        val finished = completing() + SessionCommand.Finish(id(), note = NOTE)

        assertEquals(NOTE, finished.note)
    }

    @Test
    fun `no note is no note`() {
        assertNull((completing() + SessionCommand.Finish(id())).note)
    }

    /** A field the user tabbed through is not a note. */
    @Test
    fun `a blank note is stored as nothing`() {
        assertNull((completing() + SessionCommand.Finish(id(), note = "   ")).note)
        assertNull((completing() + SessionCommand.Finish(id(), note = "")).note)
    }

    @Test
    fun `surrounding whitespace is not part of what was written`() {
        val finished = completing() + SessionCommand.Finish(id(), note = "  $NOTE\n")

        assertEquals(NOTE, finished.note)
    }

    /**
     * Nothing carries a note until the workout is finished.
     *
     * The field exists on every snapshot from the moment a session starts, and
     * this is what says the engine leaves it alone until there is something to
     * write — a note appearing mid-workout would mean something else had set it.
     */
    @Test
    fun `a workout in progress has no note`() {
        assertNull(begun().note)
        assertNull(completing().note)
    }

    /**
     * The note survives being written into the summary history reads.
     *
     * `WorkoutSummary` is otherwise derived entirely from the sets — this is the
     * one field on it that a person typed, so it is carried rather than
     * recomputed, and this is the assertion that it is carried at all.
     */
    @Test
    fun `the summary carries it`() {
        val finished = completing() + SessionCommand.Finish(id(), note = NOTE)

        assertEquals(NOTE, finished.toSummary().note)
    }

    @Test
    fun `a summary of a workout with no note has none`() {
        assertNull((completing() + SessionCommand.Finish(id())).toSummary().note)
    }

    // ---- Effort, which is one question about the workout ----

    @Test
    fun `an effort given at the end is kept`() {
        assertEquals(3, (completing() + SessionCommand.Finish(id(), effort = 3)).effort)
    }

    @Test
    fun `no effort is no effort`() {
        assertNull((completing() + SessionCommand.Finish(id())).effort)
    }

    /**
     * The engine clamps rather than trusting the caller.
     *
     * The screen offers 1-10 and is not the only thing that can send a `Finish`
     * — an import can, and so could a watch. A stored 0 or 47 would be a number
     * that looks like data and is not, and the screen being correct today is not
     * an argument about what reaches the engine tomorrow.
     */
    @Test
    fun `an effort outside the scale is brought back into it`() {
        assertEquals(1, (completing() + SessionCommand.Finish(id(), effort = 0)).effort)
        assertEquals(1, (completing() + SessionCommand.Finish(id(), effort = -3)).effort)
        assertEquals(5, (completing() + SessionCommand.Finish(id(), effort = 47)).effort)
        // The scale it used to be, which nothing may write any more.
        assertEquals(5, (completing() + SessionCommand.Finish(id(), effort = 10)).effort)
    }

    @Test
    fun `the whole scale is accepted unchanged`() {
        (1..5).forEach { value ->
            assertEquals(value, (completing() + SessionCommand.Finish(id(), effort = value)).effort)
        }
    }

    @Test
    fun `a workout in progress has no effort`() {
        assertNull(begun().effort)
        assertNull(completing().effort)
    }

    @Test
    fun `the summary carries the effort too`() {
        val finished = completing() + SessionCommand.Finish(id(), note = NOTE, effort = 4)

        assertEquals(4, finished.toSummary().effort)
        assertEquals(NOTE, finished.toSummary().note)
    }

    /**
     * Per workout, not per set.
     *
     * `SetOutcome.rpe` answers a different question and stays unwritten — asking
     * it once a set is how it stops being answered honestly. This is the
     * assertion that says the two did not get wired together.
     */
    @Test
    fun `the sets carry no effort of their own`() {
        val finished = completing() + SessionCommand.Finish(id(), effort = 4)

        assertTrue(finished.exercises.flatMap { it.sets }.isNotEmpty())
        assertTrue(finished.exercises.flatMap { it.sets }.all { it.rpe == null })
    }

    // ---- Fixtures ----

    /** One exercise of one set, so a single completion reaches `COMPLETING`. */
    private fun begun(): SessionSnapshot {
        val template = WorkoutTemplate(
            id = "plan",
            name = "Core",
            source = PlanSource.MANUAL,
            exercises = listOf(
                PlannedExercise(
                    id = "planned-0",
                    exerciseId = ExerciseId("exercise-0"),
                    position = 0,
                    target = ExerciseTarget.Reps(sets = 1, reps = 10),
                    restMs = 0L,
                ),
            ),
        )
        return engine.start("session", template) + SessionCommand.Begin(id())
    }

    private fun completing(): SessionSnapshot {
        val state = begun() + SessionCommand.CompleteSet(id(), reps = 10)
        assertEquals(SessionPhase.COMPLETING, state.phase)
        return state
    }

    private operator fun SessionSnapshot.plus(command: SessionCommand): SessionSnapshot {
        val result = engine.apply(this, command)
        assertTrue("The engine refused $command: $result", result is CommandResult.Applied)
        return (result as CommandResult.Applied).state
    }

    private companion object {
        const val NOTE = "Shoulder felt off on the last set"
    }
}
