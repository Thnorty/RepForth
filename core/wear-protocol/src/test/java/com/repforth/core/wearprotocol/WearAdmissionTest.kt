package com.repforth.core.wearprotocol

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * §20: a watch "cannot silently mutate stale state".
 *
 * The word doing the work is *silently*. A refused command is not a failure and
 * produces no error on the wrist — the phone answers with the current snapshot,
 * and the watch corrects itself. These assert both halves: that the wrong
 * things are refused, and that the refusal says what to do next.
 */
class WearAdmissionTest {

    @Test
    fun `a command aimed at the current revision is applied`() {
        assertEquals(WearAdmission.Apply, admit(command(), state()))
    }

    @Test
    fun `a command aimed at an older revision is refused`() {
        val result = admit(command(expectedRevision = 6), state(revision = 7))
        assertEquals(WearAdmission.AnswerWithCurrentState(WearRefusal.StaleRevision), result)
    }

    /**
     * The case the rule is written for.
     *
     * The user completes a set on the phone while the watch is still showing
     * the previous one, then presses complete on the watch. Without this the
     * phone would record two sets for one.
     */
    @Test
    fun `the set is not completed twice when the phone moved first`() {
        val afterPhoneCompletedASet = state(revision = 8)
        val fromTheWatchsOlderPicture = command(
            action = WearAction.CompleteSet,
            expectedRevision = 7,
        )

        assertEquals(
            WearAdmission.AnswerWithCurrentState(WearRefusal.StaleRevision),
            admit(fromTheWatchsOlderPicture, afterPhoneCompletedASet),
        )
    }

    /** A revision nobody could have observed. */
    @Test
    fun `a command from the future is refused separately`() {
        val result = admit(command(expectedRevision = 9), state(revision = 7))
        assertEquals(WearAdmission.AnswerWithCurrentState(WearRefusal.UnknownRevision), result)
    }

    @Test
    fun `a command for another session is refused`() {
        val result = admit(command(sessionId = "yesterday"), state(sessionId = "today"))
        assertEquals(WearAdmission.AnswerWithCurrentState(WearRefusal.WrongSession), result)
    }

    @Test
    fun `a command from an incompatible protocol is refused`() {
        val result = admit(command(protocolVersion = WEAR_PROTOCOL_VERSION + 1), state())
        assertEquals(
            WearAdmission.AnswerWithCurrentState(WearRefusal.UnsupportedProtocol),
            result,
        )
    }

    /**
     * Order of checks, asserted rather than assumed.
     *
     * A command that is wrong in three ways at once must report the format
     * mismatch: with a different protocol version, the session id and the
     * revision are fields whose meaning is not agreed, so reporting staleness
     * would be a guess dressed as a diagnosis.
     */
    @Test
    fun `a protocol mismatch outranks everything else it is wrong about`() {
        val wrongInEveryWay = command(
            protocolVersion = WEAR_PROTOCOL_VERSION + 1,
            sessionId = "another",
            expectedRevision = 1,
        )
        assertEquals(
            WearAdmission.AnswerWithCurrentState(WearRefusal.UnsupportedProtocol),
            admit(wrongInEveryWay, state(sessionId = "today", revision = 7)),
        )
    }

    @Test
    fun `a wrong session outranks a stale revision`() {
        val result = admit(
            command(sessionId = "another", expectedRevision = 1),
            state(sessionId = "today", revision = 7),
        )
        assertEquals(WearAdmission.AnswerWithCurrentState(WearRefusal.WrongSession), result)
    }

    /** Every action is admitted on the same terms; none is privileged. */
    @Test
    fun `no action bypasses the revision check`() {
        val refused = WearAction.entries.filter { action ->
            admit(command(action = action, expectedRevision = 1), state(revision = 7)) !=
                WearAdmission.Apply
        }
        assertEquals(WearAction.entries, refused)
    }

    private fun state(
        sessionId: String = "today",
        revision: Long = 7,
    ) = WearWorkoutState(
        sessionId = sessionId,
        revision = revision,
        phase = WearPhase.Exercise,
        exerciseId = "0025",
        exerciseName = "barbell decline wide-grip press",
        setNumber = 2,
        totalSets = 4,
        targetReps = 12,
        deadlineElapsedRealtimeMs = null,
        nextExerciseName = null,
    )

    private fun command(
        protocolVersion: Int = WEAR_PROTOCOL_VERSION,
        sessionId: String = "today",
        expectedRevision: Long = 7,
        action: WearAction = WearAction.CompleteSet,
    ) = WearCommand(
        protocolVersion = protocolVersion,
        sessionId = sessionId,
        commandId = "c1",
        expectedRevision = expectedRevision,
        sentAtElapsedRealtimeMs = 1_000L,
        action = action,
    )
}
