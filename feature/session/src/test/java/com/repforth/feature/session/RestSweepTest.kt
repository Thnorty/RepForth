package com.repforth.feature.session

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rest ring's reduced-motion path.
 *
 * The ring itself no longer animates at all — see `rememberRestSweep`, and the
 * kdoc on `RfProgressRing` for why an animated one could not work — so the only
 * arithmetic left worth asserting is the step this takes when the user has
 * asked for less movement. It has to agree with the number drawn inside the
 * ring, which is what these check.
 */
class RestSweepTest {

    @Test
    fun `a full rest is a full ring`() {
        assertEquals(1f, steppedSweep(remainingMs = 60_000L, totalMs = 60_000L), 0f)
    }

    @Test
    fun `an expired rest is an empty ring`() {
        assertEquals(0f, steppedSweep(remainingMs = 0L, totalMs = 60_000L), 0f)
    }

    /**
     * The point of the whole function.
     *
     * Anything inside the same second draws identically, so the ring moves once
     * per second rather than every time the countdown is resampled. Resampling
     * happens twice a second, and a ring that followed each sample was the
     * original complaint.
     */
    @Test
    fun `everything within one second draws the same ring`() {
        val atFiftyNine = steppedSweep(59_999L, 60_000L)

        assertEquals(atFiftyNine, steppedSweep(59_500L, 60_000L), 0f)
        assertEquals(atFiftyNine, steppedSweep(59_001L, 60_000L), 0f)
        assertEquals(59f / 60f, atFiftyNine, 0.0001f)
    }

    /**
     * The ring and the number are truncated the same way.
     *
     * `RestPanel` draws `restRemainingMs / 1000`, which is 4 with 4.6 seconds
     * left. Rounding the ring up instead would put it a whole second ahead of
     * the figure printed in the middle of it.
     */
    @Test
    fun `the ring matches the number it is drawn around`() {
        val remaining = 4_600L
        val printed = (remaining / 1000L).toInt()

        assertEquals(printed / 60f, steppedSweep(remaining, 60_000L), 0.0001f)
    }

    /** A plan with no rest cannot divide by it. */
    @Test
    fun `a rest of no length is not a division by zero`() {
        assertEquals(0f, steppedSweep(remainingMs = 1_000L, totalMs = 0L), 0f)
    }
}
