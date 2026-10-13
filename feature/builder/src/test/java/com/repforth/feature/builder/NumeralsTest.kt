package com.repforth.feature.builder

import com.repforth.core.designsystem.theme.RepForthNumeric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Figures inside a translated sentence take the numeric style.
 *
 * The review sheet says "3 × 10 reps · 90 s rest" as one localised string, so
 * the word order stays Turkish's own; the figures are found in the result
 * rather than assembled around it.
 */
class NumeralsTest {

    private fun figures(text: String): List<String> {
        val styled = text.withNumerals()
        return styled.spanStyles.map { styled.text.substring(it.start, it.end) }
    }

    @Test
    fun `every figure takes the numeric style and the words do not`() {
        val styled = "3 × 10 reps · 62,5 kg · 90 s rest".withNumerals()

        assertEquals(listOf("3", "10", "62,5", "90"), figures(styled.text))
        assertTrue(styled.spanStyles.all { it.item == RepForthNumeric.xs.toSpanStyle() })
    }

    @Test
    fun `a decimal point stays inside its figure`() {
        assertEquals(listOf("62.5"), figures("62.5 kg"))
    }

    @Test
    fun `a full stop after a figure is not part of it`() {
        assertEquals(listOf("2", "48", "45"), figures("Day 2: about 48 min, past your usual 45."))
    }

    @Test
    fun `text without figures is left alone`() {
        assertEquals(emptyList<String>(), figures("Swap for something similar"))
    }
}
