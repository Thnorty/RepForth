package com.repforth.feature.session

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The properties a synthesised sound has to have to not sound broken.
 *
 * None of these say it sounds *good* — that is the owner's ear and a device, and
 * `docs/DEVICE_TESTS.md` is where it belongs. What they cover is the handful of
 * ways generated audio goes audibly wrong, each of which is a number rather than
 * a matter of taste, and each of which is invisible until someone plays it.
 *
 * The two that matter most are the edges. A waveform that starts or ends at
 * anything other than silence starts or ends with a **step**, and a step is a
 * click — the flaw that makes generated audio sound cheap, and the first thing a
 * listener notices without being able to name it.
 */
class ChimeTest {

    private val samples = Chime.samples

    @Test
    fun `it is as long as it says it is`() {
        assertEquals(
            (Chime.SAMPLE_RATE * Chime.DURATION_MS / 1000L).toInt(),
            samples.size,
        )
    }

    /**
     * Silence at the start, or it begins with a click.
     *
     * A click is a discontinuity at the very first sample, so that is what this
     * measures — the first ten, about a fifth of a millisecond. An earlier
     * version looked at the first hundred and failed a ramp that was working: at
     * two milliseconds a three-millisecond attack is already half way up, which
     * is correct behaviour and not a step. Asserting on a window wider than the
     * ramp asserts that the ramp is slow, which is a different thing and not the
     * one that matters.
     */
    @Test
    fun `it starts from silence`() {
        assertEquals("The waveform must begin at zero", 0, samples.first().toInt())

        val opening = samples.take(10).maxOf { abs(it.toInt()) }
        assertTrue(
            "It reaches $opening within a fifth of a millisecond, which is a step",
            opening < Short.MAX_VALUE / 5,
        )
    }

    /**
     * And silence at the end, which the decay gives for free — as long as the
     * decay actually finishes inside the buffer.
     *
     * This is the assertion that catches a shortened duration or a lengthened
     * tail: either leaves the sound still ringing when the samples run out, and
     * the cut is a click.
     */
    @Test
    fun `it ends in silence rather than being cut off`() {
        val ending = samples.takeLast(200).maxOf { abs(it.toInt()) }

        assertTrue(
            "It is still at $ending when the buffer ends, so it is cut rather than faded",
            ending < Short.MAX_VALUE / 50,
        )
    }

    /**
     * It decays, which is the entire reason this is not a `ToneGenerator` beep.
     *
     * A flat tone passes every other test here. This is the one that would fail
     * if the envelope were dropped and the partials just held.
     */
    @Test
    fun `it is quieter at the end than at the start`() {
        val early = samples.slice(1_000 until 3_000).maxOf { abs(it.toInt()) }
        val late = samples.slice(samples.size - 8_000 until samples.size - 6_000)
            .maxOf { abs(it.toInt()) }

        assertTrue("Early $early should be well above late $late", early > late * 4)
    }

    /**
     * It never clips.
     *
     * Four partials summed can exceed full scale, and the result of that is not
     * a louder bell — it is a distorted one. The normalisation is measured
     * rather than assumed for this reason, and this is what holds it if the
     * partial table is ever retuned.
     */
    @Test
    fun `nothing clips`() {
        val peak = samples.maxOf { abs(it.toInt()) }

        assertTrue("Peak $peak reaches full scale", peak < Short.MAX_VALUE)
        assertTrue("Peak $peak is so low it will not be heard", peak > Short.MAX_VALUE / 2)
    }

    /** A silent buffer would pass several of the assertions above. */
    @Test
    fun `there is actually a sound in there`() {
        assertTrue(samples.any { it.toInt() != 0 })
    }

    /** Rendered once and reused, so the second alert is not a second computation. */
    @Test
    fun `the samples are computed once`() {
        assertTrue(Chime.samples === samples)
    }
}
