package com.repforth.core.wearsync

import com.repforth.core.wearprotocol.WearAction
import com.repforth.core.wearprotocol.WearCommand
import com.repforth.core.workout.SessionCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A watch request, translated into something the engine already understood. */
class WearCommandsTest {

    /**
     * The id and the revision are the two fields that make a message channel
     * safe, and both have to survive translation or the whole protocol is
     * decoration.
     */
    @Test
    fun `the command id and expected revision survive translation`() {
        val translated = command(expectedRevision = 7).toSessionCommand()

        assertEquals("c1", translated.commandId)
        assertEquals(7L, translated.expectedRevision)
    }

    /**
     * Every action produces a command that still carries its revision.
     *
     * A single branch that dropped it would create one action the watch could
     * apply to a state it had never seen, which is the failure the revision
     * exists to prevent — and it would be invisible until that action happened
     * to race.
     */
    @Test
    fun `no action loses its revision`() {
        val losing = WearAction.entries.filter { action ->
            command(action = action).toSessionCommand().expectedRevision != 7L
        }
        assertEquals(emptyList<WearAction>(), losing)
    }

    @Test
    fun `completing a set records what was planned rather than nothing`() {
        val completed = command(action = WearAction.CompleteSet).toSessionCommand()
        assertTrue(completed is SessionCommand.CompleteSet)

        // Null is the point: SessionEngine.recordSet falls back to the target,
        // so a set completed from a wrist reads as "did what was planned". A
        // zero here would record a set that was performed and achieved nothing.
        completed as SessionCommand.CompleteSet
        assertNull(completed.reps)
        assertNull(completed.weightKg)
        assertNull(completed.durationMs)
    }

    @Test
    fun `pause and resume map to their own commands`() {
        assertTrue(command(action = WearAction.Pause).toSessionCommand() is SessionCommand.Pause)
        assertTrue(command(action = WearAction.Resume).toSessionCommand() is SessionCommand.Resume)
    }

    @Test
    fun `skipping rest maps to skipping rest`() {
        assertTrue(command(action = WearAction.SkipRest).toSessionCommand() is SessionCommand.SkipRest)
    }

    /**
     * §11 names two actions for one effect.
     *
     * Recorded as a test rather than left to be discovered: both mean "leave
     * this exercise", the engine has one command for it, and the mapping is not
     * hiding a distinction it failed to implement.
     */
    @Test
    fun `both of the leaving actions mean the same command`() {
        assertTrue(
            command(action = WearAction.SkipExercise).toSessionCommand()
                is SessionCommand.NextExercise,
        )
        assertTrue(
            command(action = WearAction.NextExercise).toSessionCommand()
                is SessionCommand.NextExercise,
        )
    }

    private fun command(
        action: WearAction = WearAction.CompleteSet,
        expectedRevision: Long = 7,
    ) = WearCommand(
        sessionId = "today",
        commandId = "c1",
        expectedRevision = expectedRevision,
        sentAtElapsedRealtimeMs = 1_000L,
        action = action,
    )
}
