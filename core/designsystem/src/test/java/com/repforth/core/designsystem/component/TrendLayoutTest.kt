package com.repforth.core.designsystem.component

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the trend chart puts each entry, as plain arithmetic. */
class TrendLayoutTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun point(day: Long, value: Double) = TrendPoint(start.plusDays(day), value)

    /**
     * Two entries a month apart look a month apart. Spacing by index would draw
     * a weekly habit and a lapsed one as the same line.
     */
    @Test
    fun `points are spaced by date, not by index`() {
        val layout = trendLayout(listOf(point(0, 80.0), point(1, 81.0), point(31, 82.0)), width = 310f, height = 100f)

        assertEquals(0f, layout.dots[0].x, 0.001f)
        assertEquals(10f, layout.dots[1].x, 0.001f)
        assertEquals(310f, layout.dots[2].x, 0.001f)
    }

    @Test
    fun `the range is padded`() {
        // 80 to 90: a tenth of the span either side, so 79 to 91.
        val layout = trendLayout(listOf(point(0, 80.0), point(1, 90.0)), width = 100f, height = 120f)

        assertEquals(79.0, layout.minValue, 1e-9)
        assertEquals(91.0, layout.maxValue, 1e-9)
        assertEquals(110f, layout.dots[0].y, 0.001f)
        assertEquals(10f, layout.dots[1].y, 0.001f)
    }

    @Test
    fun `a flat series still has height`() {
        val layout = trendLayout(listOf(point(0, 80.0), point(5, 80.0)), width = 100f, height = 100f)

        assertEquals(79.5, layout.minValue, 1e-9)
        assertEquals(80.5, layout.maxValue, 1e-9)
        assertEquals(50f, layout.dots[0].y, 0.001f)
    }

    @Test
    fun `a single point is centred`() {
        val layout = trendLayout(listOf(point(0, 80.0)), width = 200f, height = 100f)

        assertEquals(100f, layout.dots.single().x, 0.001f)
        assertEquals(50f, layout.dots.single().y, 0.001f)
    }

    @Test
    fun `no points lay out nothing`() {
        assertEquals(emptyList<Any>(), trendLayout(emptyList(), width = 200f, height = 100f).dots)
    }
}
