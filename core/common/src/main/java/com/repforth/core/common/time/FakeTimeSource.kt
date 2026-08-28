package com.repforth.core.common.time

/**
 * A clock the tests drive.
 *
 * In `main` rather than `test` deliberately: every module with a timer needs it,
 * and Gradle test sources are not shareable between modules without publishing a
 * test fixtures variant for one small class. The cost is that it ships in the
 * APK; the alternative was a copy per module, which is the duplication this
 * project treats as a bug.
 *
 * Both clocks advance together by default, because that is the ordinary case;
 * [skewWallClock] moves wall time alone, which is how a timezone change or an
 * NTP correction is simulated. A countdown that survives that is a countdown
 * that is genuinely reading the monotonic source.
 */
class FakeTimeSource(
    private var wallClock: Long = 1_700_000_000_000L,
    private var monotonic: Long = 0L,
) : TimeSource {

    override fun now(): Long = wallClock

    override fun elapsedRealtime(): Long = monotonic

    /** Advances both clocks, as real time does. */
    fun advance(millis: Long) {
        require(millis >= 0) { "Time does not run backwards" }
        wallClock += millis
        monotonic += millis
    }

    /** Moves wall time only — a clock correction, not elapsed time. */
    fun skewWallClock(millis: Long) {
        wallClock += millis
    }
}
