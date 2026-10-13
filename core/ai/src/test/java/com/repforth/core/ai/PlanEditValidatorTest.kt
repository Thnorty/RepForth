package com.repforth.core.ai

import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.Muscle
import com.repforth.core.model.WorkoutLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A plan edit is judged on what it introduced.
 *
 * The plan being edited may already break rules the model must not: an exercise
 * added by hand for equipment since removed from the profile, or a day over the
 * session ceiling, which the builder allows with a warning. Rejecting an edit for
 * those would make the feature fail on exactly the plans people build by hand.
 *
 * Durations below use the app's formula: sets × (reps × 3s + rest).
 */
class PlanEditValidatorTest {

    private val ceilingMs = 30 * 60_000L

    private fun candidate(id: String, timed: Boolean = false) = ExerciseCandidate(
        id = ExerciseId(id),
        name = id,
        bodyPart = if (timed) BodyPart.CARDIO else BodyPart.CHEST,
        target = Muscle.PECTORALS,
        muscleGroup = Muscle.PECTORALS,
        secondaryMuscles = emptySet(),
        equipment = Equipment.BODY_WEIGHT,
    )

    private val offered = listOf(candidate("A"), candidate("B"), candidate("X"), candidate("T", timed = true))
        .associateBy { it.id.value }

    private fun ex(id: String, sets: Int = 3, rest: Int = 60) = EditableExercise(
        key = "key-$id", exerciseId = id, sets = sets, repetitions = 10,
        durationSeconds = null, weightKg = null, restSeconds = rest,
    )

    private fun change(
        op: PlanEditOp,
        day: Int = 1,
        position: Int,
        id: String? = null,
        sets: Int? = 3,
        reps: Int? = 10,
        duration: Int? = null,
        rest: Int? = 60,
    ) = AiPlanChange(op, day, position, id, sets, reps, duration, null, rest)

    private fun violations(
        plan: EditablePlan,
        vararg changes: AiPlanChange,
        rationale: String = "Because.",
    ): List<AiWorkoutContractViolation> {
        val applied = PlanEditApplier.apply(plan, changes.toList())
        assertTrue("Fixture must apply: $applied", applied is PlanEditApplication.Applied)
        return PlanEditValidator.validate(
            original = plan,
            edit = (applied as PlanEditApplication.Applied).edit,
            rationale = rationale,
            offered = offered,
            sessionLengthMs = ceilingMs,
        )
    }

    private fun day(vararg exercises: EditableExercise) =
        EditablePlan(listOf(EditableDay("Day", exercises.toList())))

    private fun issues(plan: EditablePlan, vararg changes: AiPlanChange) =
        violations(plan, *changes).map { it.issue }

    @Test
    fun `a valid swap has no violations`() {
        assertEquals(emptyList<AiWorkoutIssue>(), issues(day(ex("A")), change(PlanEditOp.REPLACE, position = 1, id = "X")))
    }

    @Test
    fun `an introduced exercise must be offered`() {
        val found = violations(day(ex("A")), change(PlanEditOp.REPLACE, position = 1, id = "BARBELL"))

        assertEquals(
            listOf(AiWorkoutContractViolation(AiWorkoutIssue.EXERCISE_NOT_OFFERED, dayIndex = 0, exerciseId = "BARBELL")),
            found,
        )
    }

    @Test
    fun `an off-catalog exercise the edit did not touch survives`() {
        val handBuilt = day(ex("BARBELL"), ex("A"))

        assertEquals(
            emptyList<AiWorkoutIssue>(),
            issues(handBuilt, change(PlanEditOp.ADJUST, position = 2, sets = 4, reps = null, rest = null)),
        )
    }

    @Test
    fun `introduced numbers are range-checked`() {
        assertEquals(
            listOf(AiWorkoutIssue.SETS_OUT_OF_RANGE),
            issues(day(ex("A")), change(PlanEditOp.ADD, position = 1, id = "X", sets = WorkoutLimits.sets.last + 1)),
        )
    }

    @Test
    fun `adjusted numbers are range-checked`() {
        assertEquals(
            listOf(AiWorkoutIssue.REST_OUT_OF_RANGE),
            issues(
                day(ex("A")),
                change(PlanEditOp.ADJUST, position = 1, sets = null, reps = null, rest = WorkoutLimits.restSeconds.last + 1),
            ),
        )
    }

    @Test
    fun `an introduced timed exercise needs a duration`() {
        assertEquals(
            listOf(AiWorkoutIssue.TARGET_TYPE_MISMATCH),
            issues(day(ex("A")), change(PlanEditOp.ADD, position = 1, id = "T")),
        )
    }

    @Test
    fun `an introduced exercise needs exactly one target`() {
        assertEquals(
            listOf(AiWorkoutIssue.TARGET_SHAPE),
            issues(day(ex("A")), change(PlanEditOp.ADD, position = 1, id = "X", reps = null)),
        )
    }

    @Test
    fun `a duplicate within a touched day is rejected`() {
        assertEquals(
            listOf(AiWorkoutIssue.DUPLICATE_EXERCISE),
            issues(day(ex("A"), ex("B")), change(PlanEditOp.ADD, position = 2, id = "B")),
        )
    }

    @Test
    fun `an untouched duplicate does not block an edit`() {
        val handBuilt = day(ex("B"), ex("A"), ex("B").copy(key = "second-B"))

        assertEquals(
            emptyList<AiWorkoutIssue>(),
            issues(handBuilt, change(PlanEditOp.ADJUST, position = 2, sets = 4, reps = null, rest = null)),
        )
    }

    @Test
    fun `a day may not grow past the ceiling`() {
        // 5 × (30 + 270) = 1500s, 25 minutes; adding 5 × (30 + 90) = 600s makes 35.
        val found = violations(
            day(ex("A", sets = 5, rest = 270)),
            change(PlanEditOp.ADD, position = 1, id = "X", sets = 5, rest = 90),
        )

        assertEquals(listOf(AiWorkoutContractViolation(AiWorkoutIssue.DAY_GREW_PAST_CEILING, dayIndex = 0)), found)
    }

    @Test
    fun `a day may grow while it stays under the ceiling`() {
        assertEquals(
            emptyList<AiWorkoutIssue>(),
            issues(day(ex("A", sets = 5, rest = 270)), change(PlanEditOp.ADD, position = 1, id = "X")),
        )
    }

    @Test
    fun `a day already over the ceiling may be edited if it does not grow`() {
        // 8 × 300s = 40 minutes; 7 sets is 35, still over and shorter.
        assertEquals(
            emptyList<AiWorkoutIssue>(),
            issues(
                day(ex("A", sets = 8, rest = 270)),
                change(PlanEditOp.ADJUST, position = 1, sets = 7, reps = null, rest = null),
            ),
        )
    }

    @Test
    fun `a day already over the ceiling may not grow`() {
        assertEquals(
            listOf(AiWorkoutIssue.DAY_GREW_PAST_CEILING),
            issues(
                day(ex("A", sets = 8, rest = 270)),
                change(PlanEditOp.ADJUST, position = 1, sets = 9, reps = null, rest = null),
            ),
        )
    }

    @Test
    fun `a day may not grow past maxExercisesPerDay`() {
        val full = day(*Array(WorkoutLimits.maxExercisesPerDay) { ex("E$it", sets = 1, rest = 0) })

        assertEquals(
            listOf(AiWorkoutIssue.TOO_MANY_EXERCISES),
            issues(full, change(PlanEditOp.ADD, position = 0, id = "X", sets = 1, rest = 0)),
        )
    }

    @Test
    fun `a blank rationale is rejected`() {
        assertEquals(
            listOf(AiWorkoutIssue.RATIONALE_MISSING),
            violations(day(ex("A")), change(PlanEditOp.REPLACE, position = 1, id = "X"), rationale = " ").map { it.issue },
        )
    }

    @Test
    fun `no week-level checks apply`() {
        // Two one-set exercises across a seven-day week is far under any fill
        // floor, and it is still the person's plan to edit.
        val sparse = EditablePlan(List(7) { EditableDay("Day $it", listOf(ex("A$it", sets = 1, rest = 0))) })

        assertEquals(
            emptyList<AiWorkoutIssue>(),
            issues(sparse, change(PlanEditOp.REPLACE, position = 1, id = "X", sets = 1, rest = 0)),
        )
    }
}
