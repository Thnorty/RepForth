package com.repforth.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/*
 * The phone's Material 3 scale.
 *
 * The faces, the numeric scale and `rfUiStyle` moved to `core:designtokens`
 * when the watch needed them; `Typography` is a phone Material 3 type, so it
 * could not follow. Same tokens, two assemblies.
 */

/** Material 3 scale. Deliberately quiet — labels never outrank the numeral they describe. */
val RepForthTypography = Typography(
    displayLarge = rfUiStyle(57.sp, FontWeight.Bold, 66.sp).copy(fontFamily = RepForthDisplay),
    displayMedium = rfUiStyle(45.sp, FontWeight.Bold, 52.sp).copy(fontFamily = RepForthDisplay),
    displaySmall = rfUiStyle(36.sp, FontWeight.Bold, 42.sp).copy(fontFamily = RepForthDisplay),

    // Screen titles use the display face for their weight (typography.css .rf-headline).
    headlineLarge = rfUiStyle(32.sp, FontWeight.Bold, 37.sp, (-0.01).em).copy(fontFamily = RepForthDisplay),
    headlineMedium = rfUiStyle(28.sp, FontWeight.ExtraBold, 32.sp, (-0.02).em).copy(fontFamily = RepForthDisplay),
    headlineSmall = rfUiStyle(24.sp, FontWeight.Bold, 28.sp, (-0.01).em).copy(fontFamily = RepForthDisplay),

    titleLarge = rfUiStyle(22.sp, FontWeight.Bold, 29.sp),
    titleMedium = rfUiStyle(16.sp, FontWeight.SemiBold, 21.sp),
    titleSmall = rfUiStyle(14.sp, FontWeight.SemiBold, 18.sp),

    bodyLarge = rfUiStyle(16.sp, FontWeight.Normal, 23.sp),
    bodyMedium = rfUiStyle(14.sp, FontWeight.Normal, 20.sp),
    bodySmall = rfUiStyle(12.sp, FontWeight.Normal, 17.sp),

    labelLarge = rfUiStyle(14.sp, FontWeight.SemiBold, 18.sp, 0.01.em),
    labelMedium = rfUiStyle(12.sp, FontWeight.SemiBold, 16.sp, 0.01.em),
    labelSmall = rfUiStyle(11.sp, FontWeight.Bold, 15.sp, 0.08.em),
)
