package com.repforth.core.workout

import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which plan Today offers.
 *
 * Worth testing because it is invisible when wrong: a recommendation that
 * quietly always picks the same plan looks exactly like one that is thinking.
 */
class RecommendationTest {

    private fun plan(id: String, name: String = id) = WorkoutTemplate(
        id = id,
        name = name,
        source = PlanSource.MANUAL,
        exercises = listOf(
            PlannedExercise("row-$id", ExerciseId("ex"), 0, ExerciseTarget.Reps(3, 10), 60_000),
        ),
    )

    private fun session(templateId: String?, startedAt: Long) = SessionSnapshot(
        sessionId = "s-$templateId-$startedAt",
        templateId = templateId,
        phase = SessionPhase.COMPLETED,
        exercises = emptyList(),
        startedAt = startedAt,
        endedAt = startedAt + 1,
    )

    @Test
    fun `no plans means no recommendation`() {
        assertNull(recommendNext(emptyList(), emptyList()))
    }

    @Test
    fun `with no history the first plan by name is offered`() {
        val chosen = recommendNext(listOf(plan("b", "Pull"), plan("a", "Push")), emptyList())

        assertEquals("Pull", chosen?.name)
    }

    /**
     * A plan built and never run is the one most likely to be what someone
     * meant to do next, so it outranks anything with a history.
     */
    @Test
    fun `a plan never performed outranks one performed long ago`() {
        val plans = listOf(plan("old", "Old"), plan("never", "Never"))
        val history = listOf(session("old", startedAt = 1))

        assertEquals("Never", recommendNext(plans, history)?.name)
    }

    @Test
    fun `the least recently performed plan is offered`() {
        val plans = listOf(plan("a", "A"), plan("b", "B"), plan("c", "C"))
        val history = listOf(
            session("a", startedAt = 300),
            session("b", startedAt = 100),
            session("c", startedAt = 200),
        )

        assertEquals("B", recommendNext(plans, history)?.name)
    }

    @Test
    fun `only the most recent session of a plan counts`() {
        val plans = listOf(plan("a", "A"), plan("b", "B"))
        val history = listOf(
            session("a", startedAt = 10),
            // A was also done recently, so it is not the stalest despite the
            // old session above.
            session("a", startedAt = 500),
            session("b", startedAt = 100),
        )

        assertEquals("B", recommendNext(plans, history)?.name)
    }

    /**
     * A session with no plan behind it — started from a one-off — must not make
     * some unrelated plan look recently performed.
     */
    @Test
    fun `sessions with no plan are ignored`() {
        val plans = listOf(plan("a", "A"))
        val history = listOf(session(templateId = null, startedAt = 999))

        assertEquals("A", recommendNext(plans, history)?.name)
    }

    @Test
    fun `history for a plan that no longer exists is ignored`() {
        val plans = listOf(plan("a", "A"))
        val history = listOf(session("deleted", startedAt = 999))

        assertEquals("A", recommendNext(plans, history)?.name)
    }

    /**
     * The same library must always suggest the same plan. Without a tiebreak
     * the answer would depend on the order rows came back in, which is a
     * recommendation that changes for no reason the user can see.
     */
    @Test
    fun `ties break deterministically rather than on query order`() {
        val plans = listOf(plan("z", "Zeta"), plan("a", "Alpha"))

        assertEquals("Alpha", recommendNext(plans, emptyList())?.name)
        assertEquals("Alpha", recommendNext(plans.reversed(), emptyList())?.name)
    }

    @Test
    fun `active week day in order is recommended when none performed`() {
        val day0 = com.repforth.core.model.WeekDay(0, "Push", workout = plan("d0", "Push Day"))
        val day1 = com.repforth.core.model.WeekDay(1, "Pull", workout = plan("d1", "Pull Day"))
        val week = com.repforth.core.model.TrainingWeek(
            id = "w1",
            name = "PPL",
            source = PlanSource.AI,
            active = true,
            days = listOf(day0, day1),
        )

        val recommended = recommendNext(emptyList(), emptyList(), activeWeek = week)
        assertEquals("Push Day", recommended?.name)
    }

    @Test
    fun `next unperformed day in active week is recommended`() {
        val day0 = com.repforth.core.model.WeekDay(0, "Push", workout = plan("d0", "Push Day"))
        val day1 = com.repforth.core.model.WeekDay(1, "Pull", workout = plan("d1", "Pull Day"))
        val week = com.repforth.core.model.TrainingWeek(
            id = "w1",
            name = "PPL",
            source = PlanSource.AI,
            active = true,
            days = listOf(day0, day1),
        )
        val history = listOf(session("d0", startedAt = 1000L))

        val recommended = recommendNext(emptyList(), history, activeWeek = week)
        assertEquals("Pull Day", recommended?.name)
    }
}
