package com.repforth.core.rules

import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WorkoutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §8 requires a provider's plan to be validated before it is shown, and §20 that
 * no generated plan can reference an unknown exercise id.
 *
 * These tests describe an adversary rather than a bug: in Phase 2 the plan comes
 * from outside this codebase, and the interesting cases are the ones a model
 * produces when it is confidently wrong — a plausible id that does not exist, an
 * exercise the user excluded, the same movement listed twice.
 */
class RulesValidationTest {

    private val engine = RulesEngine()

    private val bench = ExerciseCandidate(
        id = ExerciseId("0001"),
        name = "bench press",
        bodyPart = BodyPart.CHEST,
        target = Muscle.PECTORALS,
        muscleGroup = Muscle.PECTORALS,
        secondaryMuscles = setOf(Muscle.TRICEPS),
        equipment = Equipment.BARBELL,
    )
    private val curl = bench.copy(
        id = ExerciseId("0002"),
        name = "curl",
        target = Muscle.BICEPS,
        muscleGroup = Muscle.BICEPS,
        secondaryMuscles = emptySet(),
        equipment = Equipment.DUMBBELL,
    )
    private val catalog = listOf(bench, curl).associateBy { it.id }

    private fun profile(
        equipment: Set<Equipment> = emptySet(),
        exclusions: Set<MovementExclusion> = emptySet(),
        sessionMinutes: Long = 60,
    ) = UserProfile(
        id = "p",
        goal = TrainingGoal.HYPERTROPHY,
        experience = ExperienceLevel.INTERMEDIATE,
        trainingDaysPerWeek = 3,
        sessionLengthMs = sessionMinutes * 60_000,
        availableEquipment = equipment,
        preferredMuscles = emptySet(),
        exclusions = exclusions,
    )

    private fun plan(vararg ids: String) = WorkoutTemplate(
        id = "t",
        name = "from a provider",
        source = PlanSource.AI,
        exercises = ids.mapIndexed { index, id ->
            PlannedExercise(
                id = "e$index",
                exerciseId = ExerciseId(id),
                position = index,
                target = ExerciseTarget.Reps(sets = 3, reps = 10),
                restMs = 60_000,
            )
        },
    )

    @Test
    fun `a valid plan produces no violations`() {
        val violations = engine.validate(plan("0001", "0002"), GenerationRequest(profile()), catalog)
        assertEquals(emptyList<Violation>(), violations)
    }

    @Test
    fun `an invented exercise id is rejected`() {
        // The failure mode that matters most: a model returning a well-formed id
        // for an exercise that does not exist. §20 forbids it outright.
        val violations = engine.validate(plan("0001", "9999"), GenerationRequest(profile()), catalog)
        assertEquals(1, violations.size)
        assertEquals(ExerciseId("9999"), violations.single().id)
        assertTrue(violations.single().detail.contains("no such exercise"))
    }

    @Test
    fun `an excluded exercise is rejected even when the plan is otherwise sound`() {
        val request = GenerationRequest(
            profile(exclusions = setOf(MovementExclusion(ExclusionKind.EXERCISE, "0001"))),
        )
        val violations = engine.validate(plan("0001"), request, catalog)
        assertEquals(RejectionReason.EXCLUDED_EXERCISE, violations.single().reason)
    }

    @Test
    fun `an exercise working an excluded muscle secondarily is rejected`() {
        val request = GenerationRequest(
            profile(exclusions = setOf(MovementExclusion(ExclusionKind.MUSCLE, "triceps"))),
        )
        val violations = engine.validate(plan("0001"), request, catalog)
        assertEquals(RejectionReason.EXCLUDED_MUSCLE, violations.single().reason)
    }

    @Test
    fun `equipment the user does not have is rejected`() {
        val request = GenerationRequest(profile(equipment = setOf(Equipment.DUMBBELL)))
        val violations = engine.validate(plan("0001"), request, catalog)
        assertEquals(RejectionReason.EQUIPMENT_UNAVAILABLE, violations.single().reason)
    }

    @Test
    fun `the same exercise twice is rejected`() {
        val violations = engine.validate(plan("0002", "0002"), GenerationRequest(profile()), catalog)
        assertTrue(violations.any { it.reason == RejectionReason.ENOUGH_COVERAGE })
    }

    @Test
    fun `a plan longer than the session ceiling is rejected`() {
        val violations = engine.validate(
            plan("0001", "0002"),
            GenerationRequest(profile(sessionMinutes = 5)),
            catalog,
        )
        assertTrue(violations.any { it.reason == RejectionReason.NO_TIME_LEFT })
        assertTrue(violations.first { it.reason == RejectionReason.NO_TIME_LEFT }.detail.contains("ceiling"))
    }

    @Test
    fun `every violation is reported, not just the first`() {
        // A provider that gets one thing wrong usually got several wrong, and
        // fixing them one round-trip at a time is the slow path.
        val request = GenerationRequest(
            profile(exclusions = setOf(MovementExclusion(ExclusionKind.EXERCISE, "0001"))),
        )
        val violations = engine.validate(plan("0001", "9999", "0002", "0002"), request, catalog)
        assertTrue("expected at least three, got $violations", violations.size >= 3)
    }

    @Test
    fun `what the engine generates always passes its own validation`() {
        // The property that makes §8's reuse meaningful. If generate and validate
        // could disagree, "validated against the rules engine" would mean nothing.
        val request = GenerationRequest(profile(), setOf(Muscle.PECTORALS, Muscle.BICEPS), seed = 9)
        val generated = engine.generate(request, catalog.values.toList(), "generated")
        assertEquals(emptyList<Violation>(), engine.validate(generated.plan!!, request, catalog))
    }
}
