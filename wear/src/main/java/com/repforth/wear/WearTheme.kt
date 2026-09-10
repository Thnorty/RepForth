package com.repforth.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.repforth.core.designsystem.theme.DarkRepForthColors
import com.repforth.core.designsystem.theme.LocalRepForthColors
import com.repforth.core.designsystem.theme.RepForthDisplay
import com.repforth.core.designsystem.theme.Tone
import com.repforth.core.designsystem.theme.rfUiStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * RepForth on the wrist, at last.
 *
 * The watch has run on bare `MaterialTheme {}` since it was written — stock Wear
 * lavender, stock fonts — while `PROJECT_GUIDELINE` §12 asks for charcoal
 * surfaces, a single lime accent and numerals as the hero. None of that had ever
 * reached the watch, and the palette had been carrying `ambientBackground`,
 * `ambientForeground` and the rest for Wear the whole time without a caller.
 *
 * The tokens come from `core:designtokens`, which exists so this file can read
 * them: `core:designsystem` exposes phone Material 3 with `api`, and depending on
 * it here would put two different `MaterialTheme`s on one classpath.
 *
 * **Dark only, and that is not a shortcut.** A watch is a small bright object on
 * a wrist in a gym; the light palette exists for a phone held in daylight. §12's
 * "complete accessible light theme" is a phone requirement, and Wear's own
 * guidance is that an app is dark on a device whose display is off far more than
 * it is on.
 */
@Composable
fun RepForthWearTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRepForthColors provides DarkRepForthColors) {
        MaterialTheme(
            colorScheme = WearColors,
            typography = WearTypography,
            content = content,
        )
    }
}

/**
 * The dark scheme, mapped onto Wear's slots rather than the phone's.
 *
 * Wear Material 3 has its own `ColorScheme` with a different shape from the
 * phone's — no `surfaceVariant`, three `surfaceContainer` levels, and a
 * `Container`/`Content` naming that does not line up member for member. So this
 * is a deliberate mapping from the same tones, not a copy of `Theme.kt`.
 *
 * The background is [Tone.N0], pure black, where the phone uses charcoal. An
 * OLED watch spends most of its life mostly off, and black pixels are the ones
 * that are actually off; charcoal on a wrist is a lit screen pretending to be
 * dark. The charcoal tones still do the work they do on the phone, as the
 * surfaces that sit *on* that black.
 */
private val WearColors = ColorScheme(
    primary = Tone.Lime80,
    primaryDim = Tone.Lime60,
    primaryContainer = Tone.Lime20,
    onPrimary = Tone.OnPrimaryDark,
    onPrimaryContainer = Tone.Lime90,

    secondary = Tone.Sage80,
    secondaryDim = Tone.Sage70,
    secondaryContainer = Tone.Sage20,
    onSecondary = Tone.Sage10,
    onSecondaryContainer = Tone.Sage90,

    // Amber is the rest/timer role in the palette, and rest is exactly what the
    // watch's tertiary surfaces carry.
    tertiary = Tone.Amber80,
    tertiaryDim = Tone.Amber70,
    tertiaryContainer = Tone.Amber20,
    onTertiary = Tone.Amber10,
    onTertiaryContainer = Tone.Amber90,

    background = Tone.N0,
    onBackground = Tone.N98,

    surfaceContainerLow = Tone.N6,
    surfaceContainer = Tone.N12,
    surfaceContainerHigh = Tone.N17,
    onSurface = Tone.N98,
    onSurfaceVariant = Tone.Nv80,

    outline = Tone.Nv50,
    outlineVariant = Tone.Nv30,

    error = Tone.Red80,
    errorDim = Tone.Red40,
    errorContainer = Tone.Red20,
    onError = Tone.Red10,
    onErrorContainer = Tone.Red90,
)

/**
 * Wear's type scale, in the app's faces.
 *
 * Built from `rfUiStyle`, the same helper the phone's `Typography` uses, so the
 * two scales cannot drift in weight or leading. Wear's scale has three sizes per
 * role where the phone has one, and its display sizes are far smaller — a 57sp
 * `displayLarge` would not fit on a 226dp circle.
 *
 * Numerals do not come from here. They are [com.repforth.core.designsystem.theme.RepForthNumeric],
 * applied directly by the screens that show one, because §12 puts them above the
 * scale rather than in it.
 */
private val WearTypography = Typography(
    displayLarge = rfUiStyle(40.sp, FontWeight.Bold, 44.sp, (-0.02).em).copy(fontFamily = RepForthDisplay),
    displayMedium = rfUiStyle(34.sp, FontWeight.Bold, 38.sp, (-0.02).em).copy(fontFamily = RepForthDisplay),
    displaySmall = rfUiStyle(30.sp, FontWeight.Bold, 34.sp, (-0.01).em).copy(fontFamily = RepForthDisplay),

    titleLarge = rfUiStyle(20.sp, FontWeight.Bold, 24.sp).copy(fontFamily = RepForthDisplay),
    titleMedium = rfUiStyle(16.sp, FontWeight.SemiBold, 20.sp),
    titleSmall = rfUiStyle(14.sp, FontWeight.SemiBold, 18.sp),

    bodyLarge = rfUiStyle(16.sp, FontWeight.Normal, 20.sp),
    bodyMedium = rfUiStyle(14.sp, FontWeight.Normal, 18.sp),
    bodySmall = rfUiStyle(12.sp, FontWeight.Normal, 16.sp),

    labelLarge = rfUiStyle(15.sp, FontWeight.SemiBold, 19.sp, 0.01.em),
    labelMedium = rfUiStyle(13.sp, FontWeight.SemiBold, 17.sp, 0.01.em),
    labelSmall = rfUiStyle(11.sp, FontWeight.Bold, 15.sp, 0.06.em),
)
