package com.repforth.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/*
 * Material 3 semantic roles, ported from design-system/tokens/colors.css.
 * Dark is the default; light is a full peer with every role re-declared.
 *
 * Note on two roles the CSS does not name directly:
 *   background     <- --surface-app (the app canvas, one step below surface)
 *   surfaceVariant <- neutral-variant tone; the CSS only ships
 *                     --color-on-surface-variant, so the container tone is
 *                     taken from the same nv ramp.
 */

private val DarkColors = darkColorScheme(
    primary = Tone.Lime80,
    onPrimary = Tone.OnPrimaryDark,
    primaryContainer = Tone.Lime30,
    onPrimaryContainer = Tone.Lime90,
    inversePrimary = Tone.Lime35,

    secondary = Tone.Sage80,
    onSecondary = Tone.Sage20,
    secondaryContainer = Tone.Sage30,
    onSecondaryContainer = Tone.Sage90,

    tertiary = Tone.Amber80,
    onTertiary = Tone.Amber20,
    tertiaryContainer = Tone.Amber30,
    onTertiaryContainer = Tone.Amber90,

    error = Tone.Red80,
    onError = Tone.Red20,
    errorContainer = Tone.Red30,
    onErrorContainer = Tone.Red90,

    background = Tone.N6,
    onBackground = Tone.N90,

    surface = Tone.N10,
    onSurface = Tone.N90,
    surfaceVariant = Tone.Nv20,
    onSurfaceVariant = Tone.Nv80,

    surfaceContainerLowest = Tone.N4,
    surfaceContainerLow = Tone.N10,
    surfaceContainer = Tone.N12,
    surfaceContainerHigh = Tone.N17,
    surfaceContainerHighest = Tone.N22,
    surfaceDim = Tone.N6,
    surfaceBright = Tone.N30,

    outline = Tone.Nv60,
    outlineVariant = Tone.Nv30,

    inverseSurface = Tone.N90,
    inverseOnSurface = Tone.N12,

    scrim = Tone.N0,
)

private val LightColors = lightColorScheme(
    primary = Tone.Lime35,
    onPrimary = Tone.N100,
    primaryContainer = Tone.Lime90,
    onPrimaryContainer = Tone.Lime10,
    inversePrimary = Tone.Lime80,

    secondary = Tone.Sage40,
    onSecondary = Tone.N100,
    secondaryContainer = Tone.Sage90,
    onSecondaryContainer = Tone.Sage10,

    tertiary = Tone.Amber40,
    onTertiary = Tone.N100,
    tertiaryContainer = Tone.Amber90,
    onTertiaryContainer = Tone.Amber10,

    error = Tone.Red40,
    onError = Tone.N100,
    errorContainer = Tone.Red90,
    onErrorContainer = Tone.Red10,

    background = Tone.N96,
    onBackground = Tone.OnSurfaceLight,

    surface = Tone.N98,
    onSurface = Tone.OnSurfaceLight,
    surfaceVariant = Tone.Nv90,
    onSurfaceVariant = Tone.OnSurfaceVariantLight,

    surfaceContainerLowest = Tone.N100,
    surfaceContainerLow = Tone.N98,
    surfaceContainer = Tone.N96,
    surfaceContainerHigh = Tone.N94,
    surfaceContainerHighest = Tone.SurfaceContainerHighestLight,
    surfaceDim = Tone.SurfaceDimLight,
    surfaceBright = Tone.N98,

    outline = Tone.Nv50,
    outlineVariant = Tone.Nv80,

    inverseSurface = Tone.N17,
    inverseOnSurface = Tone.N94,

    scrim = Tone.N0,
)

/**
 * The RepForth theme.
 *
 * Deliberately does NOT support Material You dynamic colour: the single-accent
 * rule (one lime element per screen) and the measured AA contrast pairs both
 * break if the palette is re-derived from the user's wallpaper.
 */
@Composable
fun RepForthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extended = if (darkTheme) DarkRepForthColors else LightRepForthColors

    CompositionLocalProvider(
        LocalRepForthColors provides extended,
        // Motion belongs to the theme for the same reason the colours do: it is
        // a display decision, it reaches every screen, and providing it here is
        // what lets `rfTween` honour the setting without a single screen
        // knowing the preference exists.
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RepForthTypography,
            shapes = RepForthShapes,
            content = content,
        )
    }
}

/** Convenience accessor for the roles Material 3 does not model. */
object RepForthTheme {
    val colors: RepForthColors
        @Composable get() = LocalRepForthColors.current
}
