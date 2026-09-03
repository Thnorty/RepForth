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

    @Test
    fun `skipping a set records it as skipped rather than leaving the exercise`() {
        assertTrue(
            command(action = WearAction.SkipSet).toSessionCommand() is SessionCommand.SkipSet,
        )
    }

    @Test
    fun `leaving the exercise moves on rather than skipping one set`() {
        assertTrue(
            command(action = WearAction.NextExercise).toSessionCommand()
                is SessionCommand.NextExercise,
        )
    }

    /**
     * No two actions produce the same command.
     *
     * This is the assertion that would have caught §11's original list, where
     * `SkipExercise` and `NextExercise` were one action under two names. A
     * duplicate is not merely untidy: it spends a member of a versioned wire
     * enum, and the cost showed up as `SkipSet` being unreachable from a watch
     * while the phone could do it.
     */
    @Test
    fun `every action maps to a distinct command`() {
        val commands = WearAction.entries.map { action ->
            command(action = action).toSessionCommand()::class
        }
        assertEquals(commands.distinct().size, commands.size)
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
