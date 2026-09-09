package com.repforth.wear

import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which of §11's five screens a snapshot means — found wrong on hardware.
 *
 * Two of these were reported from a real wrist and neither could have been
 * caught by anything that existed. The screen choice is a `when` over a phase,
 * and every test around it rendered a screen that had already been chosen.
 *
 * The failures were both "the watch is showing something that is not happening",
 * which is the class this whole design is meant to make impossible: §11 gives
 * the watch no engine precisely so it cannot be wrong about the workout, only
 * out of date. A screen picked from the wrong field is a third thing — wrong
 * about the workout while perfectly up to date.
 */
class WearScreenChoiceTest {

    @Test
    fun `resting shows the rest screen`() {
        assertEquals(WearScreen.Rest, screenFor(WearPhase.Rest, restOwed = 30_000L))
    }

    /**
     * The one reported: pausing during a rest sent the wrist to a set.
     *
     * `Paused` fell through to the exercise screen, so a paused rest drew a set
     * that was not happening — with a Resume button on it, which made it read as
     * a paused *exercise*. The phone was resting; the watch said otherwise.
     */
    @Test
    fun `a paused rest is still a rest`() {
        assertEquals(WearScreen.Rest, screenFor(WearPhase.Paused, restOwed = 30_000L))
    }

    /**
     * And the pair, which is what stops the fix from swallowing the other case.
     *
     * A paused *set* is a paused set: the exercise screen is right for it, and
     * an over-eager fix that sent every pause to the rest screen would trade one
     * wrong screen for another.
     */
    @Test
    fun `a paused set is still a set`() {
        assertEquals(WearScreen.Exercise, screenFor(WearPhase.Paused, setOwed = 40_000L))
    }

    /**
     * The phone tells them apart by which clock it kept, and so does this.
     *
     * A pause has no end, so the phone drops the deadline and keeps what was
     * owed — of the two clocks, only the one that was running has a remainder.
     * No protocol field was needed to distinguish these; the answer was already
     * on the wire and nothing asked.
     */
    @Test
    fun `a pause with neither clock owed falls back to the exercise screen`() {
        assertEquals(WearScreen.Exercise, screenFor(WearPhase.Paused))
    }

    @Test
    fun `no workout is no workout`() {
        assertEquals(WearScreen.NoWorkout, WearUiState(workout = null).screen)
    }

    /**
     * Disconnected outranks everything, including a rest that is still counting.
     *
     * The snapshot may be perfectly current, but nothing the user presses will
     * arrive, and live-looking controls that do nothing are worse than a screen
     * that says why.
     */
    @Test
    fun `unreachable outranks a paused rest`() {
        val state = WearUiState(
            workout = state(WearPhase.Paused, restOwed = 30_000L),
            phoneReachable = false,
        )

        assertEquals(WearScreen.Disconnected, state.screen)
    }

    @Test
    fun `finished and abandoned both end the workout`() {
        assertEquals(WearScreen.Finished, screenFor(WearPhase.Finished))
        assertEquals(WearScreen.Finished, screenFor(WearPhase.Abandoned))
    }

    private fun screenFor(
        phase: WearPhase,
        restOwed: Long? = null,
        setOwed: Long? = null,
    ): WearScreen = WearUiState(workout = state(phase, restOwed, setOwed)).screen

    /**
     * A deadline is published as an offset from the publish clock, so "owed 30
     * seconds" is a deadline 30 seconds after it — the same arithmetic the phone
     * does, spelled out here so the fixture cannot drift from the projection.
     */
    private fun state(
        phase: WearPhase,
        restOwed: Long? = null,
        setOwed: Long? = null,
    ) = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = phase,
        exerciseId = "0025",
        exerciseName = "barbell curl",
        setNumber = 2,
        totalSets = 4,
        targetReps = 12,
        targetDurationMs = null,
        restDeadlineElapsedRealtimeMs = restOwed?.let { PUBLISHED_AT + it },
        setDeadlineElapsedRealtimeMs = setOwed?.let { PUBLISHED_AT + it },
        publishedAtElapsedRealtimeMs = PUBLISHED_AT,
        nextExerciseName = null,
    )

    private companion object {
        /** An arbitrary phone uptime, deliberately large. */
        const val PUBLISHED_AT = 595_515_000L
    }
}
