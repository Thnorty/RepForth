package com.repforth.feature.builder

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "Day 1: Day 1: Chest" bug, from both directions it arrived from.
 *
 * Found on a device, in Plans, on the first week a live provider ever returned.
 */
class WeekDayLabelTest {

    @Test
    fun `a title that names the focus is joined to the header`() {
        assertEquals("Day 1 · Chest and triceps", weekDayLabel(1, "Day 1", "Chest and triceps"))
    }

    @Test
    fun `a title the model prefixed with the day number is not prefixed twice`() {
        assertEquals("Day 1 · Chest and triceps", weekDayLabel(1, "Day 1", "Day 1: Chest and triceps"))
        assertEquals("Day 2 · Back", weekDayLabel(2, "Day 2", "Day 2 - Back"))
        assertEquals("Day 3 · Legs", weekDayLabel(3, "Day 3", "Day 3 — Legs"))
    }

    /**
     * `coach_day_default_title` and `week_day_header` are the same string, so a
     * day the model left untitled used to render its number twice with nothing
     * else on the line.
     */
    @Test
    fun `a title that is only the day number collapses to the header`() {
        assertEquals("Day 4", weekDayLabel(4, "Day 4", "Day 4"))
        assertEquals("Day 4", weekDayLabel(4, "Day 4", "  Day 4 "))
    }

    /** Turkish writes the header `1. Gün` and the fallback title `1. gün`. */
    @Test
    fun `the day number is matched regardless of case`() {
        assertEquals("1. Gün", weekDayLabel(1, "1. Gün", "1. gün"))
        assertEquals("1. Gün · Göğüs", weekDayLabel(1, "1. Gün", "1. gün: Göğüs"))
    }

    @Test
    fun `a blank title leaves the header alone`() {
        assertEquals("Day 5", weekDayLabel(5, "Day 5", ""))
        assertEquals("Day 5", weekDayLabel(5, "Day 5", "   "))
    }

    /**
     * A title and the header beside it are not always in the same language.
     *
     * The title is written by the model when the week is generated and then
     * kept; the header is rendered in whatever language the app is in now. So a
     * week generated in English and read in Turkish pairs "1. Gün" with "Day 1:
     * Chest", which an exact comparison finds nothing to strip in. Found by
     * `BuilderScreenshotTest` on its first run, having survived a unit test that
     * only ever compared a title with its own language's header.
     */
    @Test
    fun `a day number written in the other language is still stripped`() {
        assertEquals("1. Gün · Chest and triceps", weekDayLabel(1, "1. Gün", "Day 1: Chest and triceps"))
        assertEquals("Day 2 · Göğüs", weekDayLabel(2, "Day 2", "2. gün: Göğüs"))
        assertEquals("Day 3 · Legs", weekDayLabel(3, "Day 3", "Day 3 - Legs"))
    }

    /** Only this day's own number, whichever language wrote it. */
    @Test
    fun `another day's number in the other language is left alone`() {
        assertEquals("1. Gün · Day 4 recap", weekDayLabel(1, "1. Gün", "Day 4 recap"))
        assertEquals("Day 1 · 4. gün özeti", weekDayLabel(1, "Day 1", "4. gün özeti"))
    }

    /** Day 10 must not be read as Day 1 with a stray zero. */
    @Test
    fun `a longer day number is not confused with a shorter one`() {
        assertEquals("Day 1 · Day 10 recap", weekDayLabel(1, "Day 1", "Day 10 recap"))
    }
}
