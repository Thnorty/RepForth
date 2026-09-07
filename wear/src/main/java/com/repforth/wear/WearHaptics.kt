package com.repforth.wear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wrist buzz §3 asks for when a timed set or a rest reaches zero.
 *
 * A one-shot rather than a pattern, matching the phone's: this says "look at
 * me", and the screen says the rest of it. `VIBRATE` is a normal permission, so
 * there is nothing to ask the user for.
 *
 * **Whether it fires at all is decided on the phone.** §11 gives the watch no
 * settings and no storage, so the haptics preference lives in exactly one place
 * and is honoured here by the phone simply not sending the message. That also
 * means this cannot buzz for a workout that is not running: nothing else sends
 * to the alert path.
 *
 * Separate from the service that receives the message so the decision to buzz
 * and the act of buzzing are testable apart from each other — the interesting
 * half is which messages get this far.
 */
@Singleton
class WearHaptics @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun alert() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            // Wear's baseline is API 30 (§4), so this branch is live on every
            // watch below Android 12 rather than being unreachable legacy.
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(
            VibrationEffect.createOneShot(ALERT_MS, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }

    private companion object {
        /** Long enough to notice through a strap, short enough not to nag. */
        const val ALERT_MS = 400L
    }
}
