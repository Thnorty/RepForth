package com.repforth.feature.builder

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.repforth.core.designsystem.theme.RepForthNumeric

/** A figure, with a decimal part in either locale's separator. */
private val Figure = Regex("""\d+(?:[.,]\d+)?""")

/**
 * This text with every figure in `RepForthNumeric.xs`.
 *
 * Numerals are the hero, and nothing numeric renders below xs. A sentence such
 * as "3 × 10 reps · 90 s rest" stays one translated string, so each locale keeps
 * its own word order, and the figures are styled where they land in it.
 */
internal fun String.withNumerals(): AnnotatedString = buildAnnotatedString {
    append(this@withNumerals)
    val numeral = RepForthNumeric.xs.toSpanStyle()
    Figure.findAll(this@withNumerals).forEach { addStyle(numeral, it.range.first, it.range.last + 1) }
}
