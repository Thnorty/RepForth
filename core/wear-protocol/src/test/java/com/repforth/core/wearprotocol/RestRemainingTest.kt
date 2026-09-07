package com.repforth.core.wearprotocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A countdown is a duration, not a comparison of two devices' clocks.
 *
 * This exists because the first hardware test of the watch displayed a
 * 60-second rest as **591092**. The phone had been switched on for 595515
 * seconds and the watch for 4465, and the watch was subtracting the phone's
 * `elapsedRealtime` deadline from its own — which returns the difference in
 * uptimes and has nothing to do with rest.
 *
 * Nothing in the suite could have caught it, because both numbers were plain
 * `Long`s and every unit test naturally used one clock. The fix is to make the
 * subtraction happen between two of the phone's own timestamps, and these are
 * the assertions that keep it there.
 *
 * There are two clocks now — a rest and a timed set — and they share one
 * private helper for exactly this reason. Both are exercised here rather than
 * one being trusted to behave like the other, because a second copy of this
 * arithmetic is precisely the mistake that would reintroduce the bug.
 */
class RestRemainingTest {

    @Test
    fun `rest left is the gap between the phone's own two timestamps`() {
        val state = state(publishedAt = 1_000_000L, restDeadline = 1_060_000L)
        assertEquals(60_000L, state.restRemainingMs())
    }

    @Test
    fun `a timed set left is the same gap over its own field`() {
        val state = state(publishedAt = 1_000_000L, setDeadline = 1_042_000L)
        assertEquals(42_000L, state.setRemainingMs())
    }

    /**
     * The one that would have caught the bug.
     *
     * Two devices with wildly different uptimes, which is the normal case: a
     * phone stays up for weeks and a watch reboots often. The answer must not
     * change, because nothing about the *rest* changed.
     */
    @Test
    fun `the answer does not depend on how long either device has been on`() {
        val freshlyBooted = state(publishedAt = 4_465_000L, restDeadline = 4_525_000L)
        val upForAWeek = state(publishedAt = 595_515_000L, restDeadline = 595_575_000L)

        assertEquals(60_000L, freshlyBooted.restRemainingMs())
        assertEquals(60_000L, upForAWeek.restRemainingMs())
    }

    @Test
    fun `nor does it for a timed set`() {
        val freshlyBooted = state(publishedAt = 4_465_000L, setDeadline = 4_507_000L)
        val upForAWeek = state(publishedAt = 595_515_000L, setDeadline = 595_557_000L)

        assertEquals(42_000L, freshlyBooted.setRemainingMs())
        assertEquals(42_000L, upForAWeek.setRemainingMs())
    }

    @Test
    fun `a rest that has already run out reports zero rather than a negative`() {
        val overdue = state(publishedAt = 1_060_000L, restDeadline = 1_000_000L)
        assertEquals(0L, overdue.restRemainingMs())
    }

    @Test
    fun `a timed set that has already run out reports zero too`() {
        val overdue = state(publishedAt = 1_060_000L, setDeadline = 1_000_000L)
        assertEquals(0L, overdue.setRemainingMs())
    }

    @Test
    fun `no deadline means no countdown`() {
        assertNull(state(publishedAt = 1_000L).restRemainingMs())
        assertNull(state(publishedAt = 1_000L).setRemainingMs())
    }

    /**
     * The two fields are read separately, and that is the whole reason there
     * are two of them.
     *
     * One deadline the phase disambiguated would have been enough for a phone
     * that always sets it correctly, and would break silently the first time it
     * did not: the watch chooses its screen from the phase, so a shared number
     * can only ever be labelled by agreement. This asserts the fields cannot be
     * confused for each other.
     */
    @Test
    fun `a rest deadline is not read as a set deadline`() {
        val resting = state(publishedAt = 1_000_000L, restDeadline = 1_060_000L)

        assertEquals(60_000L, resting.restRemainingMs())
        assertNull(resting.setRemainingMs())
    }

    private fun state(
        publishedAt: Long,
        restDeadline: Long? = null,
        setDeadline: Long? = null,
    ) = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = WearPhase.Rest,
        exerciseId = "0025",
        exerciseName = "barbell curl",
        setNumber = 2,
        totalSets = 4,
        targetReps = 12,
        targetDurationMs = null,
        restDeadlineElapsedRealtimeMs = restDeadline,
        setDeadlineElapsedRealtimeMs = setDeadline,
        publishedAtElapsedRealtimeMs = publishedAt,
        nextExerciseName = null,
    )
}
