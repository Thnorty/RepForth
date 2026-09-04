package com.repforth.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Whether the user wants the phone to answer a tap.
 *
 * Provided by [RepForthTheme] for the same reason [LocalReducedMotion] is: it
 * is a decision that reaches several screens and none of them has another
 * reason to know that preferences exist.
 *
 * The setting shipped before anything read it. "Vibration — on completing and
 * skipping a set" was on by default, written to DataStore, read back and drawn
 * as a switch, and the repository contained no `performHapticFeedback`, no
 * `Vibrator` and no `VibrationEffect` at all — on the phone or the watch. This
 * is the same shape as the reduced-motion switch before it, which for a while
 * only swapped an animated GIF for a thumbnail.
 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * A confirmation the hand can feel, or nothing if the user turned it off.
 *
 * §7 makes haptics optional and §12 asks for them on completing and skipping a
 * set, which are the two moments in a workout when the eyes are somewhere else.
 * Read the value at composition and call it from the click handler:
 *
 * ```
 * val confirm = rfHaptic()
 * Button(onClick = { confirm(); onCompleteSet() })
 * ```
 *
 * Returning a lambda rather than performing it: a haptic belongs to an event,
 * and a composable that fires one while it composes fires it again on every
 * recomposition that follows.
 */
@Composable
@ReadOnlyComposable
fun rfHaptic(type: HapticFeedbackType = HapticFeedbackType.LongPress): () -> Unit {
    val enabled = LocalHapticsEnabled.current
    val haptics = LocalHapticFeedback.current
    return { if (enabled) haptics.performHapticFeedback(type) }
}
