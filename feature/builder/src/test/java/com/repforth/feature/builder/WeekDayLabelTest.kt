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
        assertEquals("Day 1 · Chest and triceps", weekDayLabel("Day 1", "Chest and triceps"))
    }

    @Test
    fun `a title the model prefixed with the day number is not prefixed twice`() {
        assertEquals("Day 1 · Chest and triceps", weekDayLabel("Day 1", "Day 1: Chest and triceps"))
        assertEquals("Day 2 · Back", weekDayLabel("Day 2", "Day 2 - Back"))
        assertEquals("Day 3 · Legs", weekDayLabel("Day 3", "Day 3 — Legs"))
    }

    /**
     * `coach_day_default_title` and `week_day_header` are the same string, so a
     * day the model left untitled used to render its number twice with nothing
     * else on the line.
     */
    @Test
    fun `a title that is only the day number collapses to the header`() {
        assertEquals("Day 4", weekDayLabel("Day 4", "Day 4"))
        assertEquals("Day 4", weekDayLabel("Day 4", "  Day 4 "))
    }

    /** Turkish writes the header `1. Gün` and the fallback title `1. gün`. */
    @Test
    fun `the day number is matched regardless of case`() {
        assertEquals("1. Gün", weekDayLabel("1. Gün", "1. gün"))
        assertEquals("1. Gün · Göğüs", weekDayLabel("1. Gün", "1. gün: Göğüs"))
    }

    @Test
    fun `a blank title leaves the header alone`() {
        assertEquals("Day 5", weekDayLabel("Day 5", ""))
        assertEquals("Day 5", weekDayLabel("Day 5", "   "))
    }

    /** Day 10 must not be read as Day 1 with a stray zero. */
    @Test
    fun `a longer day number is not confused with a shorter one`() {
        assertEquals("Day 1 · Day 10 recap", weekDayLabel("Day 1", "Day 10 recap"))
    }
}
