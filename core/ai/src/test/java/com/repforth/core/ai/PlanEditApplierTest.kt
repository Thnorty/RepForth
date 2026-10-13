package com.repforth.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Changes applied against the plan as sent.
 *
 * The fixture is two days: A, B, C and D, E. Every exercise carries a key, so a
 * test can tell an untouched exercise from one the edit made.
 */
class PlanEditApplierTest {

    private fun ex(id: String, sets: Int = 3, reps: Int? = 10, duration: Int? = null) =
        EditableExercise(
            key = "key-$id",
            exerciseId = id,
            sets = sets,
            repetitions = reps,
            durationSeconds = duration,
            weightKg = null,
            restSeconds = 60,
        )

    private val plan = EditablePlan(
        days = listOf(
            EditableDay("Push", listOf(ex("A"), ex("B"), ex("C"))),
            EditableDay("Pull", listOf(ex("D"), ex("E"))),
        ),
    )

    private fun replace(day: Int, position: Int, id: String) = AiPlanChange(
        op = PlanEditOp.REPLACE, day = day, position = position, exerciseId = id,
        sets = 4, repetitions = 8, durationSeconds = null, weightKg = null, restSeconds = 90,
    )

    private fun add(day: Int, after: Int, id: String) = replace(day, after, id).copy(op = PlanEditOp.ADD)

    private fun remove(day: Int, position: Int) = AiPlanChange(
        op = PlanEditOp.REMOVE, day = day, position = position, exerciseId = null,
        sets = null, repetitions = null, durationSeconds = null, weightKg = null, restSeconds = null,
    )

    private fun adjust(day: Int, position: Int, sets: Int? = null, duration: Int? = null) =
        remove(day, position).copy(op = PlanEditOp.ADJUST, sets = sets, durationSeconds = duration)

    private fun applied(vararg changes: AiPlanChange): AppliedEdit {
        val result = PlanEditApplier.apply(plan, changes.toList())
        assertTrue("Expected Applied, got $result", result is PlanEditApplication.Applied)
        return (result as PlanEditApplication.Applied).edit
    }

    private fun rejected(vararg changes: AiPlanChange): List<AiWorkoutContractViolation> {
        val result = PlanEditApplier.apply(plan, changes.toList())
        assertTrue("Expected Rejected, got $result", result is PlanEditApplication.Rejected)
        return (result as PlanEditApplication.Rejected).violations
    }

    private fun AppliedEdit.ids(day: Int) = plan.days[day].exercises.map { it.exerciseId }

    @Test
    fun `replace swaps the exercise and drops its key`() {
        val edit = applied(replace(1, 2, "X"))

        assertEquals(listOf("A", "X", "C"), edit.ids(0))
        assertNull(edit.plan.days[0].exercises[1].key)
        assertEquals(4, edit.plan.days[0].exercises[1].sets)
        assertEquals(setOf(ExerciseSlot(0, 1)), edit.changed)
        assertEquals(setOf(0), edit.touchedDays)
    }

    @Test
    fun `remove drops the exercise`() {
        val edit = applied(remove(2, 1))

        assertEquals(listOf("E"), edit.ids(1))
        assertEquals(setOf(1), edit.touchedDays)
        assertTrue(edit.changed.isEmpty())
    }

    @Test
    fun `adjust changes only the given fields and keeps the key`() {
        val edit = applied(adjust(1, 1, sets = 5))

        assertEquals(ex("A").copy(sets = 5), edit.plan.days[0].exercises[0])
        assertEquals(setOf(ExerciseSlot(0, 0)), edit.changed)
    }

    @Test
    fun `positions refer to the plan as sent`() {
        val edit = applied(remove(1, 1), add(1, 2, "Y"))

        assertEquals(listOf("B", "Y", "C"), edit.ids(0))
        assertEquals(setOf(ExerciseSlot(0, 1)), edit.changed)
    }

    @Test
    fun `add at 0 inserts at the start of the day`() {
        assertEquals(listOf("Y", "A", "B", "C"), applied(add(1, 0, "Y")).ids(0))
    }

    @Test
    fun `add after the last position appends`() {
        val edit = applied(add(1, 3, "Y"))

        assertEquals(listOf("A", "B", "C", "Y"), edit.ids(0))
        assertEquals(setOf(ExerciseSlot(0, 3)), edit.changed)
    }

    @Test
    fun `several adds after one position keep response order`() {
        assertEquals(listOf("A", "Y", "Z", "B", "C"), applied(add(1, 1, "Y"), add(1, 1, "Z")).ids(0))
    }

    @Test
    fun `a 0-based position is an unknown position`() {
        val violations = rejected(replace(1, 0, "X"))

        assertEquals(
            listOf(
                AiWorkoutContractViolation(
                    AiWorkoutIssue.UNKNOWN_POSITION,
                    dayIndex = 0,
                    exerciseId = "X",
                    position = 0,
                ),
            ),
            violations,
        )
    }

    @Test
    fun `a day outside the plan is an unknown position`() {
        assertEquals(
            listOf(AiWorkoutIssue.UNKNOWN_POSITION),
            rejected(remove(3, 1)).map { it.issue },
        )
    }

    @Test
    fun `an add past the end of the day is an unknown position`() {
        assertEquals(listOf(AiWorkoutIssue.UNKNOWN_POSITION), rejected(add(2, 3, "Y")).map { it.issue })
    }

    @Test
    fun `two changes to one position conflict`() {
        val violations = rejected(adjust(1, 2, sets = 4), remove(1, 2))

        assertEquals(
            listOf(AiWorkoutContractViolation(AiWorkoutIssue.CONFLICTING_CHANGES, dayIndex = 0, position = 2)),
            violations,
        )
    }

    @Test
    fun `remove carrying an exercise id breaks the operation shape`() {
        assertEquals(
            listOf(AiWorkoutIssue.OPERATION_SHAPE),
            rejected(remove(1, 1).copy(exerciseId = "X")).map { it.issue },
        )
    }

    @Test
    fun `adjust with nothing to change breaks the operation shape`() {
        assertEquals(listOf(AiWorkoutIssue.OPERATION_SHAPE), rejected(adjust(1, 1)).map { it.issue })
    }

    @Test
    fun `adjust naming an exercise breaks the operation shape`() {
        assertEquals(
            listOf(AiWorkoutIssue.OPERATION_SHAPE),
            rejected(adjust(1, 1, sets = 4).copy(exerciseId = "X")).map { it.issue },
        )
    }

    @Test
    fun `replace without sets breaks the operation shape`() {
        assertEquals(
            listOf(AiWorkoutIssue.OPERATION_SHAPE),
            rejected(replace(1, 1, "X").copy(sets = null)).map { it.issue },
        )
    }

    @Test
    fun `add without an exercise breaks the operation shape`() {
        assertEquals(
            listOf(AiWorkoutIssue.OPERATION_SHAPE),
            rejected(add(1, 1, "X").copy(exerciseId = null)).map { it.issue },
        )
    }

    @Test
    fun `adjust giving duration to a repetition exercise is a target type mismatch`() {
        assertEquals(
            listOf(AiWorkoutIssue.TARGET_TYPE_MISMATCH),
            rejected(adjust(1, 1, duration = 30)).map { it.issue },
        )
    }

    @Test
    fun `emptying a day is rejected`() {
        val violations = rejected(remove(2, 1), remove(2, 2))

        assertEquals(listOf(AiWorkoutContractViolation(AiWorkoutIssue.EMPTY_PLAN, dayIndex = 1)), violations)
    }

    @Test
    fun `more than maxChanges is rejected before anything is applied`() {
        val tooMany = List(PlanEditLimits.maxChanges + 1) { add(1, 0, "Y$it") }

        assertEquals(
            listOf(AiWorkoutContractViolation(AiWorkoutIssue.TOO_MANY_CHANGES)),
            rejected(*tooMany.toTypedArray()),
        )
    }

    @Test
    fun `exactly maxChanges is allowed`() {
        val edit = applied(*List(PlanEditLimits.maxChanges) { add(2, 2, "Y$it") }.toTypedArray())

        assertEquals(2 + PlanEditLimits.maxChanges, edit.plan.days[1].exercises.size)
    }

    @Test
    fun `untouched exercises are the same instances`() {
        val edit = applied(replace(1, 1, "X"))

        assertSame(plan.days[0].exercises[2], edit.plan.days[0].exercises[2])
        assertSame(plan.days[1], edit.plan.days[1])
    }

    @Test
    fun `no changes apply to the same plan`() {
        val edit = applied()

        assertEquals(plan, edit.plan)
        assertTrue(edit.changed.isEmpty())
        assertTrue(edit.touchedDays.isEmpty())
    }

    @Test
    fun `a violation names its position in the retry feedback`() {
        val feedback = AiWorkoutRetryFeedback.fromContract(rejected(replace(1, 0, "X")))

        assertTrue(feedback.issues.single().describe().startsWith("day 1, position 0, exercise X: "))
    }
}
