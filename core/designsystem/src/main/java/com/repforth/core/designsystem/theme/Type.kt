package com.repforth.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

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
 * FONTS — ACTION NEEDED. The design system flags Archivo (numeric/display) and
 * Manrope (UI) as *substitutions*; no font binaries were ever supplied. Until
 * they are, both fall back to the platform default, which changes the texture
 * but not the hierarchy. Drop the files into core/designsystem/src/main/res/font/
 * and point RepForthDisplay / RepForthUi at them — nothing else needs to change.
 */

val RepForthDisplay: FontFamily = FontFamily.Default   // TODO: Archivo
val RepForthUi: FontFamily = FontFamily.Default        // TODO: Manrope

private val Tight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun ui(
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
    lineHeightStyle = Tight,
)

/** Material 3 scale. Deliberately quiet — labels never outrank the numeral they describe. */
val RepForthTypography = Typography(
    displayLarge = ui(57.sp, FontWeight.Bold, 66.sp).copy(fontFamily = RepForthDisplay),
    displayMedium = ui(45.sp, FontWeight.Bold, 52.sp).copy(fontFamily = RepForthDisplay),
    displaySmall = ui(36.sp, FontWeight.Bold, 42.sp).copy(fontFamily = RepForthDisplay),

    // Screen titles use the display face for their weight (typography.css .rf-headline).
    headlineLarge = ui(32.sp, FontWeight.Bold, 37.sp, (-0.01).em).copy(fontFamily = RepForthDisplay),
    headlineMedium = ui(28.sp, FontWeight.ExtraBold, 32.sp, (-0.02).em).copy(fontFamily = RepForthDisplay),
    headlineSmall = ui(24.sp, FontWeight.Bold, 28.sp, (-0.01).em).copy(fontFamily = RepForthDisplay),

    titleLarge = ui(22.sp, FontWeight.Bold, 29.sp),
    titleMedium = ui(16.sp, FontWeight.SemiBold, 21.sp),
    titleSmall = ui(14.sp, FontWeight.SemiBold, 18.sp),

    bodyLarge = ui(16.sp, FontWeight.Normal, 23.sp),
    bodyMedium = ui(14.sp, FontWeight.Normal, 20.sp),
    bodySmall = ui(12.sp, FontWeight.Normal, 17.sp),

    labelLarge = ui(14.sp, FontWeight.SemiBold, 18.sp, 0.01.em),
    labelMedium = ui(12.sp, FontWeight.SemiBold, 16.sp, 0.01.em),
    labelSmall = ui(11.sp, FontWeight.Bold, 15.sp, 0.08.em),
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
    lineHeightStyle = Tight,
    // Tabular figures keep countdowns from jittering as digits change. Requires a
    // font that ships the `tnum` feature — a no-op on the current fallback face.
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
