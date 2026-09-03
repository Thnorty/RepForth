package com.repforth.core.wearprotocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rest countdown is a duration, not a comparison of two devices' clocks.
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
 */
class RestRemainingTest {

    @Test
    fun `rest left is the gap between the phone's own two timestamps`() {
        val state = state(publishedAt = 1_000_000L, deadline = 1_060_000L)
        assertEquals(60_000L, state.restRemainingMs())
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
        val freshlyBooted = state(publishedAt = 4_465_000L, deadline = 4_525_000L)
        val upForAWeek = state(publishedAt = 595_515_000L, deadline = 595_575_000L)

        assertEquals(60_000L, freshlyBooted.restRemainingMs())
        assertEquals(60_000L, upForAWeek.restRemainingMs())
    }

    @Test
    fun `a rest that has already run out reports zero rather than a negative`() {
        val overdue = state(publishedAt = 1_060_000L, deadline = 1_000_000L)
        assertEquals(0L, overdue.restRemainingMs())
    }

    @Test
    fun `no deadline means no countdown`() {
        assertNull(state(publishedAt = 1_000L, deadline = null).restRemainingMs())
    }

    private fun state(publishedAt: Long, deadline: Long?) = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = WearPhase.Rest,
        exerciseId = "0025",
        exerciseName = "barbell curl",
        setNumber = 2,
        totalSets = 4,
        targetReps = 12,
        deadlineElapsedRealtimeMs = deadline,
        publishedAtElapsedRealtimeMs = publishedAt,
        nextExerciseName = null,
    )
}
