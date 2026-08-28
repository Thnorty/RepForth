package com.repforth.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Ported from design-system/tokens/palette.css and tokens/colors.css.
 *
 * Layering matches the CSS exactly:
 *   1. Raw tones below are the equivalent of palette.css. They are `internal` on
 *      purpose — product code must never reference a tone directly, only the
 *      semantic roles exposed through MaterialTheme.colorScheme / LocalRepForthColors.
 *   2. Semantic roles are Material 3 ColorScheme members wherever M3 has one.
 *   3. Roles M3 has no slot for (info, numeric, track, Wear ambient) live in
 *      RepForthColors below.
 *
 * Dark is the default. Light is a full peer, not a tint — every role is
 * re-declared, exactly as colors.css requires.
 */

// ---------------------------------------------------------------- raw tones

internal object Tone {
    // Lime — the single accent.
    val Lime10 = Color(0xFF182100)
    val Lime20 = Color(0xFF2F4000)
    val Lime30 = Color(0xFF476000)
    val Lime35 = Color(0xFF4A6600)
    val Lime40 = Color(0xFF5F8000)
    val Lime50 = Color(0xFF7BA800)
    val Lime60 = Color(0xFF94C222)
    val Lime70 = Color(0xFFADDB3F)
    val Lime80 = Color(0xFFC6F45C)
    val Lime90 = Color(0xFFDFFF9B)
    val Lime95 = Color(0xFFEEFFC6)
    val Lime99 = Color(0xFFFBFFEF)

    // Sage — completed / secondary.
    val Sage10 = Color(0xFF181E08)
    val Sage20 = Color(0xFF2C331A)
    val Sage30 = Color(0xFF424A2F)
    val Sage40 = Color(0xFF5A6146)
    val Sage70 = Color(0xFFA8B08C)
    val Sage80 = Color(0xFFC3CCA6)
    val Sage90 = Color(0xFFDFE8C1)

    // Amber — rest / timer / tertiary.
    val Amber10 = Color(0xFF241A00)
    val Amber20 = Color(0xFF3F2E00)
    val Amber30 = Color(0xFF5A4400)
    val Amber40 = Color(0xFF6F5300)
    val Amber70 = Color(0xFFDFAB3C)
    val Amber80 = Color(0xFFF0C36B)
    val Amber90 = Color(0xFFFFDFA0)

    // Sky — informational (watch connected, sync-free notices).
    val Sky10 = Color(0xFF001B3D)
    val Sky30 = Color(0xFF1C4E9C)
    val Sky40 = Color(0xFF2B5EA7)
    val Sky80 = Color(0xFFA6C8FF)
    val Sky90 = Color(0xFFD6E3FF)

    // Red — destructive.
    val Red10 = Color(0xFF410002)
    val Red20 = Color(0xFF690005)
    val Red30 = Color(0xFF93000A)
    val Red40 = Color(0xFFBA1A1A)
    val Red80 = Color(0xFFFFB4AB)
    val Red90 = Color(0xFFFFDAD6)

    // Neutral — carries a faint green cast so charcoal belongs to the lime family.
    val N0 = Color(0xFF000000)
    val N4 = Color(0xFF0B0F07)
    val N6 = Color(0xFF101408)
    val N10 = Color(0xFF14180E)
    val N12 = Color(0xFF191E12)
    val N17 = Color(0xFF23281B)
    val N22 = Color(0xFF2E3325)
    val N30 = Color(0xFF43483A)
    val N40 = Color(0xFF5A6050)
    val N50 = Color(0xFF737968)
    val N60 = Color(0xFF8D9381)
    val N70 = Color(0xFFA7AE9A)
    val N80 = Color(0xFFC3CAB4)
    val N90 = Color(0xFFDFE6CF)
    val N94 = Color(0xFFEDF4DC)
    val N96 = Color(0xFFF3FAE2)
    val N98 = Color(0xFFFAFFE9)
    val N100 = Color(0xFFFFFFFF)

    // Neutral variant — outlines and hairlines.
    val Nv20 = Color(0xFF2A2F20)
    val Nv30 = Color(0xFF454A3C)
    val Nv50 = Color(0xFF757B69)
    val Nv60 = Color(0xFF8F9582)
    val Nv80 = Color(0xFFC5CBB4)
    val Nv90 = Color(0xFFE1E7D0)

    // Literals that colors.css declares directly rather than via a tone.
    val OnPrimaryDark = Color(0xFF253300)
    val OnSurfaceLight = Color(0xFF1A1E14)
    val OnSurfaceVariantLight = Color(0xFF454A3C)
    val SurfaceContainerHighestLight = Color(0xFFE7EED6)
    val SurfaceDimLight = Color(0xFFDDE4CD)
    val TextStrongLight = Color(0xFF10140A)
}

// ------------------------------------------------- roles M3 has no slot for

/**
 * RepForth roles that Material 3's [androidx.compose.material3.ColorScheme] does
 * not model. Reach these through [LocalRepForthColors], never by hard-coding a tone.
 *
 * `info` is a full tonal role (watch connectivity, sync-free notices). `numeric`
 * and `track` back the numbers-are-the-hero type system and progress rails. The
 * `ambient*` values are Wear OS always-on rendering: pure black, dim monochrome,
 * no fills.
 */
@Immutable
data class RepForthColors(
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    /** Loudest text tone — used by the numeric hero, above onSurface. */
    val textStrong: Color,
    /** Default colour for tabular numerals. */
    val numeric: Color,
    /** Numerals that carry the accent (active timer, current set). */
    val numericAccent: Color,
    /** Unfilled portion of progress rails, rings and sliders. */
    val track: Color,
    val ambientBackground: Color,
    val ambientForeground: Color,
    val ambientQuiet: Color,
    val ambientOutline: Color,
)

internal val DarkRepForthColors = RepForthColors(
    info = Tone.Sky80,
    onInfo = Tone.Sky10,
    infoContainer = Tone.Sky30,
    onInfoContainer = Tone.Sky90,
    textStrong = Tone.N98,
    numeric = Tone.N98,
    numericAccent = Tone.Lime80,
    track = Tone.N90.copy(alpha = 0.14f),
    ambientBackground = Color(0xFF000000),
    ambientForeground = Color(0xFFDDE3D2),
    ambientQuiet = Color(0xFF7E8474),
    ambientOutline = Color(0xFF4A4F42),
)

internal val LightRepForthColors = RepForthColors(
    info = Tone.Sky30,
    onInfo = Tone.N100,
    infoContainer = Tone.Sky90,
    onInfoContainer = Tone.Sky10,
    textStrong = Tone.TextStrongLight,
    numeric = Tone.TextStrongLight,
    numericAccent = Tone.Lime35,
    track = Tone.OnSurfaceLight.copy(alpha = 0.12f),
    // Ambient is a Wear-only concern and is identical in both themes: the
    // always-on face is always black.
    ambientBackground = Color(0xFF000000),
    ambientForeground = Color(0xFFDDE3D2),
    ambientQuiet = Color(0xFF7E8474),
    ambientOutline = Color(0xFF4A4F42),
)

val LocalRepForthColors = staticCompositionLocalOf { DarkRepForthColors }
