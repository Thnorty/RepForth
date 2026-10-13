package com.repforth.feature.builder

import com.repforth.core.ai.AiPlanChange
import com.repforth.core.ai.EditableDay
import com.repforth.core.ai.EditableExercise
import com.repforth.core.ai.EditablePlan
import com.repforth.core.ai.PlanEditApplication
import com.repforth.core.ai.PlanEditApplier
import com.repforth.core.ai.PlanEditOp
import com.repforth.core.ai.PlanRef
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.Muscle
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Between the builder's drafts and the edit contract, in both directions.
 *
 * The direction that matters most is back: an exercise the edit only adjusted is
 * still the person's row -- its identity, its unused target half and its
 * progression carry -- and a mapping that rebuilt it from the wire would lose all
 * three without anything on screen looking different.
 */
class PlanEditMappingTest {

    private fun draft(
        id: String,
        exerciseId: String,
        name: String = "Name $exerciseId",
        sets: Int = 3,
        reps: Int = 10,
        duration: Int = 45,
        timed: Boolean = false,
        weight: Double? = null,
        progress: Double = 0.0,
        loaded: Double? = null,
    ) = DraftExercise(
        id = id,
        exerciseId = ExerciseId(exerciseId),
        name = name,
        sets = sets,
        reps = reps,
        durationSeconds = duration,
        weightKg = weight,
        restSeconds = 60,
        timed = timed,
        progressKg = progress,
        loadedWeightKg = loaded,
    )

    private val single = BuilderUiState(
        name = "Push day",
        exercises = listOf(
            draft("d1", "press", weight = 40.0, progress = 2.5, loaded = 40.0),
            draft("d2", "plank", timed = true, duration = 30),
        ),
    )

    private val week = BuilderUiState(
        name = "Week",
        weekDays = listOf(
            DraftWeekDay(0, "Push", templateId = "t-push", dayOfWeek = DayOfWeek.MONDAY, notes = "heavy",
                exercises = listOf(draft("w1", "press"))),
            DraftWeekDay(1, "Pull", templateId = "t-pull", exercises = listOf(draft("w2", "row"), draft("w3", "curl"))),
        ),
    )

    private val introduced = mapOf(
        ExerciseId("fly") to ExerciseSummary(ExerciseId("fly"), "Cable fly", BodyPart.CHEST, Muscle.PECTORALS, Equipment.CABLE),
    )

    private fun change(op: PlanEditOp, day: Int, position: Int, id: String? = null, sets: Int? = null, weight: Double? = null) =
        AiPlanChange(
            op, day, position, id,
            sets = sets ?: if (id != null) 4 else null,
            repetitions = if (id != null) 12 else null,
            durationSeconds = null,
            weightKg = weight,
            restSeconds = if (id != null) 90 else null,
        )

    private fun BuilderUiState.applying(vararg changes: AiPlanChange): BuilderUiState {
        val applied = PlanEditApplier.apply(toEditablePlan(), changes.toList()) as PlanEditApplication.Applied
        return withEditedPlan(applied.edit.plan, introduced)
    }

    @Test
    fun `a single workout is sent as one day titled with the plan name`() {
        val plan = single.toEditablePlan()

        assertEquals(1, plan.days.size)
        assertEquals("Push day", plan.days.single().title)
    }

    @Test
    fun `each exercise is keyed by its draft id and sends only its active target`() {
        assertEquals(
            listOf(
                EditableExercise("d1", "press", 3, 10, null, 40.0, 60),
                EditableExercise("d2", "plank", 3, null, 30, null, 60),
            ),
            single.toEditablePlan().days.single().exercises,
        )
    }

    @Test
    fun `a week is sent day by day, with its titles`() {
        assertEquals(
            EditablePlan(
                listOf(
                    EditableDay("Push", listOf(EditableExercise("w1", "press", 3, 10, null, null, 60))),
                    EditableDay(
                        "Pull",
                        listOf(
                            EditableExercise("w2", "row", 3, 10, null, null, 60),
                            EditableExercise("w3", "curl", 3, 10, null, null, 60),
                        ),
                    ),
                ),
            ),
            week.toEditablePlan(),
        )
    }

    @Test
    fun `an adjusted exercise keeps its draft identity and its other target half`() {
        val after = single.applying(change(PlanEditOp.ADJUST, 1, 1, sets = 5)).exercises.first()

        assertEquals(single.exercises.first().copy(sets = 5), after)
        assertEquals(45, after.durationSeconds)
    }

    @Test
    fun `adjusting only sets keeps the progression carry`() {
        val after = single.applying(change(PlanEditOp.ADJUST, 1, 1, sets = 5)).exercises.first()

        assertEquals(2.5, after.carriedProgressKg, 0.0)
    }

    @Test
    fun `adjusting the weight drops the carry`() {
        val after = single.applying(change(PlanEditOp.ADJUST, 1, 1, weight = 45.0)).exercises.first()

        assertEquals(45.0, after.weightKg)
        assertEquals(0.0, after.carriedProgressKg, 0.0)
    }

    @Test
    fun `a replaced exercise is a new draft with the catalog's name`() {
        val after = single.applying(change(PlanEditOp.REPLACE, 1, 1, id = "fly")).exercises.first()

        assertNotEquals("d1", after.id)
        assertEquals(ExerciseId("fly"), after.exerciseId)
        assertEquals("Cable fly", after.name)
        assertEquals(4, after.sets)
        assertEquals(12, after.reps)
    }

    @Test
    fun `untouched exercises are the same drafts`() {
        val after = single.applying(change(PlanEditOp.REPLACE, 1, 1, id = "fly"))

        assertTrue(after.exercises[1] === single.exercises[1])
    }

    @Test
    fun `week days keep their template id, weekday and notes`() {
        val after = week.applying(change(PlanEditOp.ADD, 1, 1, id = "fly")).weekDays.first()

        assertEquals("t-push", after.templateId)
        assertEquals(DayOfWeek.MONDAY, after.dayOfWeek)
        assertEquals("heavy", after.notes)
        assertEquals(listOf("press", "fly"), after.exercises.map { it.exerciseId.value })
    }

    @Test
    fun `a rename while reviewing survives apply`() {
        // The proposal was computed from `week`; the person renamed the days
        // while reading it. Apply must keep their titles.
        val edited = (PlanEditApplier.apply(week.toEditablePlan(), listOf(change(PlanEditOp.ADD, 1, 1, id = "fly")))
            as PlanEditApplication.Applied).edit.plan
        val renamed = week.copy(weekDays = week.weekDays.map { it.copy(title = it.title + "!") })

        val after = renamed.withEditedPlan(edited, introduced)

        assertEquals(listOf("Push!", "Pull!"), after.weekDays.map { it.title })
        assertEquals(listOf("press", "fly"), after.weekDays.first().exercises.map { it.exerciseId.value })
    }

    @Test
    fun `review rows read the plan as sent`() {
        val rows = week.reviewRows(
            listOf(
                change(PlanEditOp.REPLACE, 1, 1, id = "fly"),
                change(PlanEditOp.ADD, 2, 1, id = "fly"),
                change(PlanEditOp.ADD, 2, 0, id = "fly"),
                change(PlanEditOp.REMOVE, 2, 2),
                change(PlanEditOp.ADJUST, 2, 1, sets = 5),
            ),
            introduced,
        )

        assertEquals(
            listOf(
                PlanEditRow.Replaced(1, "Name press", "Cable fly", introduced.values.single().thumbnail, Prescription(4, 12, null, null, 90)),
                PlanEditRow.Added(2, "Cable fly", introduced.values.single().thumbnail, "Name row", Prescription(4, 12, null, null, 90)),
                PlanEditRow.Added(2, "Cable fly", introduced.values.single().thumbnail, null, Prescription(4, 12, null, null, 90)),
                PlanEditRow.Removed(2, "Name curl", week.weekDays[1].exercises[1].thumbnail),
                PlanEditRow.Adjusted(
                    2, "Name row", week.weekDays[1].exercises[0].thumbnail,
                    before = Prescription(3, 10, null, null, 60),
                    after = Prescription(5, 10, null, null, 60),
                ),
            ),
            rows,
        )
    }

    @Test
    fun `a single workout's rows have no day number`() {
        val row = single.reviewRows(listOf(change(PlanEditOp.REMOVE, 1, 2)), introduced).single()

        assertNull(row.dayNumber)
    }

    @Test
    fun `a pin names the exercise and its day`() {
        assertEquals(PinnedExercise(PlanRef(2, 2), "Name curl", 2), week.pinnedAt(PlanRef(2, 2)))
        assertEquals(PinnedExercise(PlanRef(1, 1), "Name press", null), single.pinnedAt(PlanRef(1, 1)))
        assertNull(single.pinnedAt(PlanRef(1, 9)))
    }

    @Test
    fun `the basis ignores names, so a rename does not make a proposal stale`() {
        val renamed = week.copy(name = "Other", weekDays = week.weekDays.map { it.copy(title = "x") })

        assertEquals(week.planEditBasis, renamed.planEditBasis)
        assertNotEquals(week.planEditBasis, week.applying(change(PlanEditOp.REMOVE, 2, 1)).planEditBasis)
    }
}
