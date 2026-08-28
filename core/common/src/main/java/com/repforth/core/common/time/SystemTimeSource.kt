package com.repforth.core.common.time

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real clocks.
 *
 * `SystemClock.elapsedRealtime` rather than `uptimeMillis`: it keeps counting
 * while the device is in deep sleep, which is exactly what a rest timer must do
 * when the screen goes off mid-workout.
 */
@Singleton
class SystemTimeSource @Inject constructor() : TimeSource {
    override fun now(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
