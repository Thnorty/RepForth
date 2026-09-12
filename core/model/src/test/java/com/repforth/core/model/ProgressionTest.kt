package com.repforth.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an easy workout does to the plan that made it easy. */
class ProgressionTest {

    // ------------------------------------------------------- reading the scale

    @Test
    fun `the scale runs from easy up to hard, and the middle does nothing`() {
        assertEquals(2, progressionLevels(1))
        assertEquals(1, progressionLevels(2))
        assertEquals(0, progressionLevels(3))
        assertEquals(-1, progressionLevels(4))
        assertEquals(-2, progressionLevels(5))
    }

    /** An unanswered question is not a middling answer. Both move nothing. */
    @Test
    fun `an unanswered question moves nothing`() {
        assertEquals(0, progressionLevels(null))
    }

    // --------------------------------------------------------- the load ceiling

    /**
     * **A tenth of a heavy lift is far too much for one session.**
     *
     * Twelve kilograms on a hundred and twenty kilogram squat is what a pure
     * percentage would prescribe after one good workout, and nobody would write
     * that into a plan by hand. The ceiling is what holds it to a step.
     */
    @Test
    fun `a heavy lift moves by a step, not by a tenth`() {
        assertEquals(5.0, loadLevelKg(120.0), 0.001)
        assertEquals(5.0, loadLevelKg(50.0), 0.001)
    }

    /** And below the ceiling the tenth is what binds, which is the other half. */
    @Test
    fun `a light load moves by a tenth, not by a step`() {
        assertEquals(1.0, loadLevelKg(10.0), 0.001)
        assertEquals(3.0, loadLevelKg(30.0), 0.001)
    }

    // ------------------------------------------------------ the worked example

    /**
     * **The case this whole design exists for**, walked session by session.
     *
     * A ten kilogram dumbbell told "a little easy" every time. The plan is
     * written in fives, so for two sessions it reads the same and the progress
     * is real underneath; on the third the number earns its move.
     *
     * A pure five kilogram step would have made it fifteen on the first
     * session, which is half again. Rounding a percentage to the grid instead
     * would have made it ten forever, so the offer would have done nothing at
     * all and looked broken.
     */
    @Test
    fun `a light dumbbell creeps for two sessions and then moves`() {
        var exercise = planned(ExerciseTarget.Reps(sets = 3, reps = 12, weightKg = 10.0))

        val shown = mutableListOf<Double>()
        val exact = mutableListOf<Double>()
        repeat(6) {
            exercise = exercise.progressedBy(1)
            shown += exercise.target.weightKg!!
            exact += exercise.target.weightKg!! + exercise.progressKg
        }

        assertEquals(listOf(10.0, 10.0, 15.0, 15.0, 15.0, 20.0), shown)
        listOf(11.0, 12.1, 13.31, 14.64, 16.11, 17.72).forEachIndexed { index, expected ->
            assertEquals("session ${index + 1}", expected, exact[index], 0.01)
        }
    }

    /** Two levels is exactly twice one, which is what "too easy" buys. */
    @Test
    fun `too easy moves twice as far underneath`() {
        val once = planned(ExerciseTarget.Reps(3, 12, 10.0)).progressedBy(1)
        val twice = planned(ExerciseTarget.Reps(3, 12, 10.0)).progressedBy(2)

        assertEquals(11.0, once.target.weightKg!! + once.progressKg, 0.001)
        assertEquals(12.0, twice.target.weightKg!! + twice.progressKg, 0.001)
    }

    /** A heavy lift shows the difference on the plan rather than underneath it. */
    @Test
    fun `a heavy lift separates the two answers on screen`() {
        assertEquals(
            65.0,
            planned(ExerciseTarget.Reps(4, 8, 60.0)).progressedBy(1).target.weightKg,
        )
        assertEquals(
            70.0,
            planned(ExerciseTarget.Reps(4, 8, 60.0)).progressedBy(2).target.weightKg,
        )
    }

    // ------------------------------------------------ what moves, and what does not

    /**
     * **A loaded exercise moves its load whatever it is measured in.** Adding
     * seconds to a weighted hold changes what is being trained; adding plate
     * does not.
     */
    @Test
    fun `a weighted plank gets heavier rather than longer`() {
        val after = planned(ExerciseTarget.Duration(3, 45_000L, 60.0)).progressedBy(1)
        val target = after.target as ExerciseTarget.Duration

        assertEquals("The clock is not what a loaded hold progresses", 45_000L, target.durationMs)
        assertEquals(65.0, target.weightKg)
    }

    /** Even when the load is too light to show it, the clock stays put. */
    @Test
    fun `a lightly weighted plank still does not gain seconds`() {
        val after = planned(ExerciseTarget.Duration(3, 45_000L, 20.0)).progressedBy(1)
        val target = after.target as ExerciseTarget.Duration

        assertEquals(45_000L, target.durationMs)
        assertEquals(20.0, target.weightKg)
        assertEquals("Two kilograms of it, underneath", 2.0, after.progressKg, 0.001)
    }

    @Test
    fun `an unloaded plank gains ten seconds`() {
        val after = planned(ExerciseTarget.Duration(3, 45_000L)).progressedBy(1)
        assertEquals(55_000L, (after.target as ExerciseTarget.Duration).durationMs)
    }

    @Test
    fun `an unloaded rep exercise gains a repetition`() {
        val after = planned(ExerciseTarget.Reps(3, 12)).progressedBy(1)
        assertEquals(13, (after.target as ExerciseTarget.Reps).reps)
    }

    /** Nothing happens for "Just right", and the plan is not even rebuilt. */
    @Test
    fun `zero levels returns the very same plan`() {
        val template = template()
        assertTrue("A no-op must not rewrite the plan", template === template.progressedBy(0))
    }

    // --------------------------------------------------------------- the limits

    /**
     * A load already at the floor cannot get lighter, and the screen must be
     * able to tell: nothing moved, so there is nothing to offer about it.
     */
    @Test
    fun `a load at the floor stays put and reports that it did not move`() {
        val change = template(ExerciseTarget.Reps(3, 12, 5.0)).progressionPreview(-2).single()

        assertEquals(5.0, change.after.weightKg)
        assertFalse("Nothing moved, visibly or otherwise", change.moves)
    }

    @Test
    fun `a single repetition cannot be taken away`() {
        val after = planned(ExerciseTarget.Reps(3, 1)).progressedBy(-2)
        assertEquals(1, (after.target as ExerciseTarget.Reps).reps)
    }

    // -------------------------------------------------------------- the preview

    /**
     * The two answers the finish screen is built around: which rows to draw,
     * and which exercises to describe in a sentence instead.
     */
    @Test
    fun `the preview separates what shows from what only creeps`() {
        val plan = WorkoutTemplate(
            id = "p",
            name = "Push Day",
            source = PlanSource.MANUAL,
            exercises = listOf(
                planned(ExerciseTarget.Reps(4, 8, 60.0), position = 0),
                planned(ExerciseTarget.Reps(3, 12, 10.0), position = 1),
            ),
        )

        val preview = plan.progressionPreview(1)

        assertTrue("A heavy lift moves on the plan", preview[0].isVisible)
        assertFalse("A light one does not, yet", preview[1].isVisible)
        assertTrue("But it does move", preview[1].moves)
        assertEquals(15.0, preview[1].creepingToward)
    }

    /** There is nothing to creep toward when there is no load to creep with. */
    @Test
    fun `an unloaded exercise creeps toward nothing`() {
        assertNull(template(ExerciseTarget.Reps(3, 12)).progressionPreview(1).single().creepingToward)
    }

    private fun planned(target: ExerciseTarget, position: Int = 0) = PlannedExercise(
        id = "e$position",
        exerciseId = ExerciseId("00$position"),
        position = position,
        target = target,
        restMs = 90_000L,
    )

    private fun template(
        target: ExerciseTarget = ExerciseTarget.Reps(4, 8, 60.0),
    ) = WorkoutTemplate(
        id = "p",
        name = "Push Day",
        source = PlanSource.MANUAL,
        exercises = listOf(planned(target)),
    )
}
