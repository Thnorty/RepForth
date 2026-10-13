package com.repforth.feature.body

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The date picker offers no day after today.
 *
 * Tested on the `SelectableDates` the picker is given rather than through the
 * picker's own day cells, whose semantics are Material's and not ours to pin.
 */
class NotInFutureTest {

    private val today = LocalDate.of(2026, 10, 13)
    private val dates = NotInFuture(today)

    private fun millis(date: LocalDate) = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `today and earlier can be picked`() {
        assertTrue(dates.isSelectableDate(millis(today)))
        assertTrue(dates.isSelectableDate(millis(today.minusYears(2))))
    }

    @Test
    fun `tomorrow cannot`() {
        assertFalse(dates.isSelectableDate(millis(today.plusDays(1))))
    }

    @Test
    fun `next year cannot`() {
        assertTrue(dates.isSelectableYear(2026))
        assertFalse(dates.isSelectableYear(2027))
    }
}
