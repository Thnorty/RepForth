package com.repforth.core.ai

import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.Language
import com.repforth.core.model.Muscle
import com.repforth.core.model.WorkoutLimits
import com.repforth.core.testing.sampleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the model is told when asked to change a plan.
 *
 * The plan travels as addressed rows, so every change the model makes can name
 * exactly one exercise; the person's words travel verbatim and capped.
 */
class AiPlanEditPromptTest {

    private fun candidate(
        id: String,
        name: String,
        muscle: Muscle = Muscle.PECTORALS,
        timed: Boolean = false,
        equipment: Equipment = Equipment.BODY_WEIGHT,
    ) = ExerciseCandidate(
        id = ExerciseId(id),
        name = name,
        bodyPart = if (timed) BodyPart.CARDIO else BodyPart.CHEST,
        target = muscle,
        muscleGroup = muscle,
        secondaryMuscles = emptySet(),
        equipment = equipment,
    )

    private val catalog = listOf(
        candidate("0025", "barbell bench press", equipment = Equipment.BARBELL),
        candidate("0662", "push-up"),
        candidate("1160", "jumping jack", timed = true),
    )

    private fun ex(id: String, sets: Int = 4, reps: Int? = 8, duration: Int? = null, weight: Double? = 60.0, rest: Int = 90) =
        EditableExercise("key-$id", id, sets, reps, duration, weight, rest)

    private val plan = EditablePlan(
        listOf(
            EditableDay("Push", listOf(ex("0662", sets = 3, reps = 12, weight = null, rest = 60))),
            EditableDay(
                "Upper",
                listOf(ex("0025"), ex("1160", sets = 3, reps = null, duration = 30, weight = null, rest = 45)),
            ),
        ),
    )

    private fun request(
        instruction: String = "add more chest",
        focus: PlanRef? = null,
        editPlan: EditablePlan = plan,
    ) = AiPlanEditRequest.from(
        request = PlanEditRequest(sampleProfile(), editPlan, instruction, focus),
        locale = Language.ENGLISH,
        catalog = catalog,
        eligible = catalog.filter { it.equipment == Equipment.BODY_WEIGHT },
    )

    private fun prompt(focus: PlanRef? = null, feedback: AiWorkoutRetryFeedback? = null) =
        request(focus = focus).toPlanEditPrompt(feedback)

    @Test
    fun `the instruction is sent verbatim, inside the brief`() {
        val text = request(instruction = "Göğüs için daha fazla hareket ekle").toPlanEditPrompt(null)
        val brief = text.substringAfter("BRIEF").substringBefore("DAYS")

        assertTrue(brief, brief.contains("Göğüs için daha fazla hareket ekle"))
    }

    @Test
    fun `every plan exercise is one addressed row`() {
        assertTrue(prompt().lines().contains("2.1|0025|barbell bench press|pectorals|4|8 reps|60|90"))
    }

    @Test
    fun `a timed row says seconds and an empty weight`() {
        assertTrue(prompt().lines().contains("2.2|1160|jumping jack|pectorals|3|30s||45"))
    }

    @Test
    fun `whole-number weights are written without a decimal point`() {
        assertFalse(prompt().contains("|60.0|"))
    }

    @Test
    fun `the focus line appears only when there is a focus`() {
        assertTrue(prompt(focus = PlanRef(2, 1)).lines().any { it.startsWith("FOCUS: 2.1") })
        assertFalse(prompt().contains("FOCUS:"))
    }

    @Test
    fun `day titles are listed so a day can be named`() {
        val lines = prompt().lines()

        assertTrue(lines.contains("DAY 1: Push"))
        assertTrue(lines.contains("DAY 2: Upper"))
    }

    @Test
    fun `an exercise the catalog no longer has still makes a row`() {
        val withVanished = EditablePlan(listOf(EditableDay("Day", listOf(ex("9999")))))

        val text = request(editPlan = withVanished).toPlanEditPrompt(null)

        assertTrue(text.lines().contains("1.1|9999|9999|unknown|4|8 reps|60|90"))
    }

    @Test
    fun `the catalog offered is the eligible one, not the whole catalog`() {
        val rows = prompt().substringAfter("CATALOG").lines()

        assertTrue(rows.any { it.startsWith("0662|push-up|") })
        assertFalse(rows.any { it.startsWith("0025|") })
    }

    @Test
    fun `the prompt states the limits from WorkoutLimits and PlanEditLimits`() {
        val text = prompt()

        assertTrue(text.contains("sets ${WorkoutLimits.sets.first}-${WorkoutLimits.sets.last}"))
        assertTrue(text.contains("rest_seconds ${WorkoutLimits.restSeconds.first}-${WorkoutLimits.restSeconds.last}"))
        assertTrue(text.contains("at most ${PlanEditLimits.maxChanges} changes"))
        assertTrue(text.contains("${WorkoutLimits.secondsPerRepEstimate} + rest_seconds"))
    }

    @Test
    fun `positions are explained as the plan as sent`() {
        val text = prompt()

        assertTrue(text.contains("add inserts after"))
        assertTrue(text.contains("0 means"))
    }

    /**
     * An edit may run a day past the session length (owner's decision,
     * 2026-10-13), so the prompt must not tell the model otherwise: a model told
     * that going over is rejected answers "add more chest" with a swap.
     */
    @Test
    fun `going over the session length is allowed, and said to be`() {
        val time = prompt().substringAfter("TIME").substringBefore("NUMBERS")

        assertTrue(time, time.contains("may run past"))
        assertFalse(time, time.contains("rejected"))
    }

    @Test
    fun `the instruction is capped`() {
        assertEquals(PlanEditLimits.maxInstructionChars, request(instruction = "x".repeat(2_000)).instruction.length)
    }

    @Test
    fun `retry feedback is listed, and no provider text is`() {
        val feedback = AiWorkoutRetryFeedback.fromContract(
            listOf(AiWorkoutContractViolation(AiWorkoutIssue.UNKNOWN_POSITION, dayIndex = 0, position = 0)),
        )

        val text = prompt(feedback = feedback)

        assertTrue(text.contains("return the whole list of changes again"))
        assertTrue(text.lines().any { it.startsWith("- day 1, position 0: ") })
    }
}
