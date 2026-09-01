package com.repforth.core.model

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrainingWeekTest {

    private val sampleTemplate = WorkoutTemplate(
        id = "tmpl-1",
        name = "Push Day",
        source = PlanSource.MANUAL,
        exercises = listOf(
            PlannedExercise(
                id = "pe-1",
                exerciseId = ExerciseId("ex-1"),
                position = 0,
                target = ExerciseTarget.Reps(sets = 3, reps = 10),
                restMs = 60_000L,
            ),
        ),
    )

    @Test
    fun `a valid week constructs successfully`() {
        val week = TrainingWeek(
            id = "w-1",
            name = "Push Pull Legs",
            source = PlanSource.AI,
            active = true,
            days = listOf(
                WeekDay(position = 0, title = "Push", dayOfWeek = DayOfWeek.MONDAY, workout = sampleTemplate),
                WeekDay(position = 1, title = "Pull", dayOfWeek = DayOfWeek.WEDNESDAY, workout = sampleTemplate),
                WeekDay(position = 2, title = "Legs", dayOfWeek = DayOfWeek.FRIDAY, workout = sampleTemplate),
            ),
        )

        assertEquals("w-1", week.id)
        assertEquals(3, week.days.size)
        assertEquals(sampleTemplate.estimatedDurationMs * 3, week.estimatedDurationMs)
    }

    @Test
    fun `a blank name is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            TrainingWeek(
                id = "w-1",
                name = "   ",
                source = PlanSource.MANUAL,
                active = false,
                days = emptyList(),
            )
        }
    }

    @Test
    fun `non-contiguous day positions are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            TrainingWeek(
                id = "w-1",
                name = "Split",
                source = PlanSource.MANUAL,
                active = false,
                days = listOf(
                    WeekDay(position = 0, title = "Day 1", workout = sampleTemplate),
                    WeekDay(position = 2, title = "Day 2", workout = sampleTemplate),
                ),
            )
        }
    }

    @Test
    fun `duplicate assigned days of week are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            TrainingWeek(
                id = "w-1",
                name = "Split",
                source = PlanSource.MANUAL,
                active = false,
                days = listOf(
                    WeekDay(position = 0, title = "Day 1", dayOfWeek = DayOfWeek.MONDAY, workout = sampleTemplate),
                    WeekDay(position = 1, title = "Day 2", dayOfWeek = DayOfWeek.MONDAY, workout = sampleTemplate),
                ),
            )
        }
    }

    @Test
    fun `multiple null days of week are permitted`() {
        val week = TrainingWeek(
            id = "w-1",
            name = "Split",
            source = PlanSource.MANUAL,
            active = false,
            days = listOf(
                WeekDay(position = 0, title = "Day 1", dayOfWeek = null, workout = sampleTemplate),
                WeekDay(position = 1, title = "Day 2", dayOfWeek = null, workout = sampleTemplate),
            ),
        )
        assertEquals(2, week.days.size)
    }
}
