package com.repforth.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.repforth.core.designtokens.R

/*
 * Ported from design-system/tokens/typography.css.
 *
 * Two roles only:
 *   NUMERIC (heavy, tabular) — reps, weight, sets, countdowns. The hero.
 *   UI      (quiet)          — everything else.
 *
 * Sizes are sp, so Android font scaling to 200% works by construction. Never
 * put a fixed height on a container holding these styles.
 *
 * FONTS. Archivo (display + numerals) and Manrope (UI) ship as static weights in
 * res/font/ — only the weights the token set actually declares, so nothing
 * unused is packaged. Both cover Latin Extended-A, which is what makes Turkish
 * render correctly, and both carry the "tnum" feature, which is what stops a
 * running countdown from jittering as its digits change.
 *
 * Licensed under the SIL Open Font License; see /licenses.
 */

/** Archivo — display and the numeric hero. Bold 700 for titles, ExtraBold 800 for numerals. */
val RepForthDisplay: FontFamily = FontFamily(
    Font(R.font.archivo_bold, FontWeight.Bold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
)

/** Manrope — everything else, deliberately quiet. Weights 400-700 per the design system. */
val RepForthUi: FontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

val RfTightLeading = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * One quiet UI text style.
 *
 * Public because two Material scales are built from it now: the phone's
 * `Typography` in `core:designsystem`, and the watch's in `:wear`. It was a
 * private helper while there was only one.
 */
fun rfUiStyle(
    size: TextUnit,
    weight: FontWeight,
    lineHeight: TextUnit,
    tracking: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = RepForthUi,
    fontSize = size,
    fontWeight = weight,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    lineHeightStyle = RfTightLeading,
)

/**
 * The numeric scale. Material 3 has no slot for it, but it is the product's core
 * idea — so it is a first-class token set rather than ad-hoc styling.
 *
 * Weight 800, tabular figures, tracking -0.02em, leading 0.92. Nothing numeric
 * ever renders below [xs]; if a figure does not fit, cut the label instead.
 */
@Immutable
data class RepForthNumericStyles(
    val hero: TextStyle,
    val xl: TextStyle,
    val lg: TextStyle,
    val md: TextStyle,
    val sm: TextStyle,
    val xs: TextStyle,
)

private fun numeric(size: TextUnit) = TextStyle(
    fontFamily = RepForthDisplay,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size,
    lineHeight = size * 0.92f,
    letterSpacing = (-0.02).em,
    lineHeightStyle = RfTightLeading,
    // Tabular figures: every digit gets the same advance width, so a running
    // countdown does not shift as its digits change. Verified present in Archivo.
    fontFeatureSettings = "tnum",
)

val RepForthNumeric = RepForthNumericStyles(
    hero = numeric(96.sp),
    xl = numeric(72.sp),
    lg = numeric(56.sp),
    md = numeric(40.sp),
    sm = numeric(28.sp),
    xs = numeric(20.sp),
)
