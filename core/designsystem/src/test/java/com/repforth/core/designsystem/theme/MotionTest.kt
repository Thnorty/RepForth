package com.repforth.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reduced motion means no motion, not less of it.
 *
 * The design system's own CSS clamps every duration to 1ms under
 * `prefers-reduced-motion`; there is no global clamp on Compose, so the
 * collapse happens here and this is the assertion that it happens at all.
 */
class MotionTest {

    @Test
    fun `a duration survives when the user has not asked for less movement`() {
        assertEquals(Dur.medium, reducedDuration(Dur.medium, reduced = false))
    }

    @Test
    fun `a duration collapses to nothing when the user has`() {
        assertEquals(0, reducedDuration(Dur.medium, reduced = true))
    }

    /** Every token, so a new one cannot be added that skips the collapse. */
    @Test
    fun `every duration token collapses`() {
        val all = listOf(Dur.instant, Dur.quick, Dur.short, Dur.medium, Dur.long, Dur.xlong)
        assertEquals(List(all.size) { 0 }, all.map { reducedDuration(it, reduced = true) })
    }

    /** The tokens are in ascending order, which is the only thing that names them. */
    @Test
    fun `the duration scale ascends`() {
        val all = listOf(Dur.instant, Dur.quick, Dur.short, Dur.medium, Dur.long, Dur.xlong)
        assertEquals(all.sorted(), all)
    }
}
