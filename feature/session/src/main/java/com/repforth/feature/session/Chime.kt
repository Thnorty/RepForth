package com.repforth.feature.session

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The sound a timer makes when it reaches zero: a struck bell, computed here.
 *
 * **Still generated rather than shipped**, which is the property the previous
 * `ToneGenerator` beep was chosen for and the reason this is arithmetic instead
 * of a `.ogg`. §6 keeps media with someone else's provenance out of this
 * repository, and a bundled sound would be one more thing with a licence to
 * track for a noise that plays twice a set.
 *
 * What changed is that `ToneGenerator` cannot make this sound. Its tones are
 * telephony signals — a flat sine held for a fixed time and then cut — and the
 * thing that makes a bell a bell is the part it has no way to express: **the
 * decay**. So the samples are synthesised.
 *
 * ### Why it sounds like metal
 *
 * A bell is not a note. It is a handful of partials that are *not* whole-number
 * multiples of a fundamental, each dying at its own rate — and the second half
 * of that is what the ear reads as "struck metal" rather than "organ". Higher
 * partials fade first, so the timbre darkens as it rings out. Hold them all for
 * the same length and it stops sounding struck at all.
 *
 * The ratios are a tubular bell's. They are irrational against the fundamental
 * on purpose; a harmonic series here would sound like a chord.
 */
internal object Chime {

    const val SAMPLE_RATE: Int = 44_100

    /**
     * Long enough for the ring to end by itself.
     *
     * This was 1200ms, and at that length the fade below had to start while the
     * bell was still clearly audible — which is heard as the sound being cut,
     * because it is. Most of the extra time is spent below the level anyone
     * notices; what it buys is that the fade begins at a whisper instead of
     * partway through the ring.
     */
    const val DURATION_MS: Long = 1_800L

    /** Rendered once. About 160 KB, held for the life of the process. */
    val samples: ShortArray by lazy { render() }

    /**
     * A tubular bell's partials: ratio, relative strength, and how fast it dies.
     *
     * The fundamental rings for most of a second; the top partial is gone in a
     * fifth of one. That spread is the whole effect.
     */
    private val partials = listOf(
        Partial(ratio = 1.00, amplitude = 1.00, decaySeconds = 0.55),
        Partial(ratio = 2.76, amplitude = 0.55, decaySeconds = 0.32),
        Partial(ratio = 5.40, amplitude = 0.35, decaySeconds = 0.20),
        Partial(ratio = 8.93, amplitude = 0.18, decaySeconds = 0.12),
    )

    private data class Partial(val ratio: Double, val amplitude: Double, val decaySeconds: Double)

    private fun render(): ShortArray {
        val count = (SAMPLE_RATE * DURATION_MS / 1000L).toInt()
        val raw = DoubleArray(count)

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            var value = 0.0
            for (partial in partials) {
                val frequency = FUNDAMENTAL_HZ * partial.ratio
                value += partial.amplitude *
                    exp(-t / partial.decaySeconds) *
                    sin(2.0 * PI * frequency * t)
            }
            // A few milliseconds of ramp, because a waveform that begins at
            // full amplitude begins with a step, and a step is a click.
            raw[i] = value * (1.0 - exp(-t / ATTACK_SECONDS)) * release(t)
        }

        // Normalised against what was actually produced rather than against the
        // sum of the amplitudes above. The partials do not peak together — they
        // are at unrelated frequencies — so the arithmetic bound is far above
        // the real one, and using it would make this needlessly quiet. Measuring
        // also means the headroom survives someone retuning the table.
        val peak = raw.maxOf { kotlin.math.abs(it) }.coerceAtLeast(MIN_PEAK)
        val scale = Short.MAX_VALUE * HEADROOM / peak

        return ShortArray(count) { i -> (raw[i] * scale).toInt().toShort() }
    }

    /**
     * The fade that takes the tail to exact silence.
     *
     * **An exponential decay never reaches zero, and the buffer does.** Without
     * this the fundamental was still at 30% of full amplitude when the samples
     * ran out, and every timer ended on a hard cut.
     *
     * The first attempt fixed that on paper and still sounded wrong — reported
     * as "it starts fading, then it cuts". Two things were the matter, and the
     * fade being *present* was not one of them:
     *
     * - **It was linear.** A straight ramp has a corner where it starts and
     *   another where it stops, and a corner in an envelope is heard as an
     *   event. A raised cosine leaves at zero slope and arrives at zero slope,
     *   so there is nothing to hear beginning or ending.
     * - **It was 80ms, starting at about a tenth of full amplitude.** Fading an
     *   audible sound over a twelfth of a second *is* cutting it, however smooth
     *   the curve. The buffer is longer now so the fade begins at roughly a
     *   fortieth of full amplitude — already close to silence — and takes 250ms
     *   to finish the job.
     *
     * The natural decay does the work the ear is listening to; this only removes
     * the step at the very end.
     */
    private fun release(t: Double): Double {
        val secondsLeft = DURATION_MS / 1000.0 - t
        if (secondsLeft >= RELEASE_SECONDS) return 1.0
        return 0.5 * (1.0 - cos(PI * secondsLeft / RELEASE_SECONDS))
    }

    /**
     * A5. High enough to carry over a gym and out of a phone speaker, low enough
     * that its fourth partial at nearly 8 kHz is still a sound rather than hiss.
     */
    private const val FUNDAMENTAL_HZ = 880.0

    private const val ATTACK_SECONDS = 0.003

    private const val RELEASE_SECONDS = 0.25

    /** Short of full scale, so the sum of the partials cannot clip. */
    private const val HEADROOM = 0.85

    private const val MIN_PEAK = 1e-9
}
