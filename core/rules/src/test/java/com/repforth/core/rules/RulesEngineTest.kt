package com.repforth.core.rules

import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.PlanSource
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §8 says no generated plan may violate a hard constraint. That is a claim about
 * every possible input, so these tests aim at the ways a selector usually leaks:
 * a constraint checked only on the primary muscle, a second pass that forgets the
 * first pass's filters, an ordering that depends on what the database returned.
 */
class RulesEngineTest {

    private val engine = RulesEngine()

    private fun candidate(
        id: String,
        target: Muscle,
        equipment: Equipment = Equipment.DUMBBELL,
        secondary: Set<Muscle> = emptySet(),
        bodyPart: BodyPart = BodyPart.UPPER_ARMS,
        muscleGroup: Muscle = target,
    ) = ExerciseCandidate(
        id = ExerciseId(id),
        name = "exercise $id",
        bodyPart = bodyPart,
        target = target,
        muscleGroup = muscleGroup,
        secondaryMuscles = secondary,
        equipment = equipment,
    )

    private fun profile(
        goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
        experience: ExperienceLevel = ExperienceLevel.INTERMEDIATE,
        equipment: Set<Equipment> = emptySet(),
        exclusions: Set<MovementExclusion> = emptySet(),
        preferred: Set<Muscle> = emptySet(),
        sessionMinutes: Long = 60,
    ) = UserProfile(
        id = "p",
        goal = goal,
        experience = experience,
        trainingDaysPerWeek = 4,
        sessionLengthMs = sessionMinutes * 60_000,
        availableEquipment = equipment,
        preferredMuscles = preferred,
        exclusions = exclusions,
    )

    private val library = listOf(
        candidate("0001", Muscle.PECTORALS, secondary = setOf(Muscle.TRICEPS)),
        candidate("0002", Muscle.PECTORALS, Equipment.BARBELL),
        candidate("0003", Muscle.LATS, secondary = setOf(Muscle.BICEPS)),
        candidate("0004", Muscle.QUADS, Equipment.BODY_WEIGHT),
        candidate("0005", Muscle.BICEPS),
        candidate("0006", Muscle.TRICEPS),
        candidate("0007", Muscle.ABDOMINALS, Equipment.BODY_WEIGHT),
        candidate("0008", Muscle.CARDIOVASCULAR_SYSTEM, Equipment.STATIONARY_BIKE, bodyPart = BodyPart.CARDIO),
    )

    private fun generate(request: GenerationRequest) = engine.generate(request, library, "Test plan")

    @Test
    fun `candidate filtering is stable and exposes the same hard-rule boundary`() {
        val excluded = library.first()
        val request = GenerationRequest(
            profile = profile(
                exclusions = setOf(
                    MovementExclusion(ExclusionKind.EXERCISE, excluded.id.value),
                ),
            ),
        )

        val forward = engine.filterCandidates(request, library)
        val reversed = engine.filterCandidates(request, library.reversed())

        assertEquals(forward, reversed)
        assertTrue(excluded !in forward.eligibleCandidates)
        assertEquals(
            RejectionReason.EXCLUDED_EXERCISE,
            forward.rejections.single { it.id == excluded.id }.reason,
        )
        assertEquals(
            forward.eligibleCandidates.map { it.id.value }.sorted(),
            forward.eligibleCandidates.map { it.id.value },
        )
    }

    // ── Determinism ──────────────────────────────────────────────────────────

    @Test
    fun `the same seed always produces the same plan`() {
        val request = GenerationRequest(profile(), setOf(Muscle.PECTORALS), seed = 42)
        val first = generate(request).plan!!.exercises.map { it.exerciseId }
        val second = generate(request).plan!!.exercises.map { it.exerciseId }
        assertEquals(first, second)
    }

    @Test
    fun `candidate order does not change the plan`() {
        // Candidates arrive in whatever order SQLite returned. A plan that
        // depended on that would be unreproducible from a bug report.
        val request = GenerationRequest(profile(), setOf(Muscle.PECTORALS, Muscle.LATS), seed = 7)
        val forwards = engine.generate(request, library, "p").plan!!.exercises.map { it.exerciseId }
        val backwards = engine.generate(request, library.reversed(), "p").plan!!.exercises.map { it.exerciseId }
        assertEquals(forwards, backwards)
    }

    // ── Hard constraints ─────────────────────────────────────────────────────

    @Test
    fun `an excluded exercise is never selected`() {
        val request = GenerationRequest(
            profile(exclusions = setOf(MovementExclusion(ExclusionKind.EXERCISE, "0001"))),
            setOf(Muscle.PECTORALS),
        )
        val outcome = generate(request)
        assertFalse(outcome.plan!!.exercises.any { it.exerciseId == ExerciseId("0001") })
        assertTrue(Rejection(ExerciseId("0001"), RejectionReason.EXCLUDED_EXERCISE) in outcome.rejections)
    }

    @Test
    fun `an excluded muscle disqualifies an exercise that only works it secondarily`() {
        // The leak this is aimed at: checking exclusions against the target
        // muscle only. Someone avoiding their triceps does not want them worked
        // incidentally by a press.
        val request = GenerationRequest(
            profile(exclusions = setOf(MovementExclusion(ExclusionKind.MUSCLE, "triceps"))),
            setOf(Muscle.PECTORALS),
        )
        val plan = generate(request).plan!!
        assertFalse(
            "0001 works triceps as a secondary muscle",
            plan.exercises.any { it.exerciseId == ExerciseId("0001") },
        )
    }

    @Test
    fun `excluding a muscle by one name excludes its synonym too`() {
        val request = GenerationRequest(
            // The candidate is labelled `abdominals`; the user excluded `abs`.
            profile(exclusions = setOf(MovementExclusion(ExclusionKind.MUSCLE, "abs"))),
            targetMuscles = emptySet(),
        )
        val plan = generate(request).plan!!
        assertFalse(plan.exercises.any { it.exerciseId == ExerciseId("0007") })
    }

    @Test
    fun `unavailable equipment is never programmed`() {
        val request = GenerationRequest(
            profile(equipment = setOf(Equipment.BODY_WEIGHT)),
            setOf(Muscle.PECTORALS, Muscle.QUADS),
        )
        val plan = generate(request).plan!!
        assertTrue(plan.exercises.isNotEmpty())
        assertTrue(
            plan.exercises.all { planned ->
                library.first { it.id == planned.exerciseId }.equipment == Equipment.BODY_WEIGHT
            },
        )
    }

    @Test
    fun `an empty equipment set means unknown, not nothing`() {
        // A fresh profile has stated no equipment. Treating that as "owns
        // nothing" would produce an empty plan and look like a broken app.
        val outcome = generate(GenerationRequest(profile(equipment = emptySet()), setOf(Muscle.PECTORALS)))
        assertNotNull(outcome.plan)
        assertTrue(outcome.plan!!.exercises.isNotEmpty())
    }

    @Test
    fun `a plan never exceeds the session ceiling`() {
        // 15 minutes buys very little at four sets of ten with 90 seconds rest.
        val request = GenerationRequest(profile(sessionMinutes = 15), seed = 3)
        val plan = generate(request).plan!!
        assertTrue(
            "plan runs ${plan.estimatedDurationMs / 60_000} minutes",
            plan.estimatedDurationMs <= 15 * 60_000,
        )
    }

    @Test
    fun `no exercise appears twice in a plan`() {
        val plan = generate(GenerationRequest(profile(), seed = 11)).plan!!
        val ids = plan.exercises.map { it.exerciseId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `positions are contiguous from zero`() {
        val plan = generate(GenerationRequest(profile(), seed = 5)).plan!!
        assertEquals(plan.exercises.indices.toList(), plan.exercises.map { it.position })
    }

    // ── Coverage ─────────────────────────────────────────────────────────────

    @Test
    fun `every requested muscle gets an exercise before any gets a second`() {
        val request = GenerationRequest(profile(), setOf(Muscle.PECTORALS, Muscle.LATS, Muscle.QUADS), seed = 1)
        val plan = generate(request).plan!!
        val covered = plan.exercises.map { planned ->
            library.first { it.id == planned.exerciseId }
        }
        listOf(Muscle.PECTORALS, Muscle.LATS, Muscle.QUADS).forEach { muscle ->
            assertTrue("$muscle uncovered", covered.any { it.primarilyHits(muscle) })
        }
    }

    @Test
    fun `a muscle nothing can serve is reported rather than silently dropped`() {
        val request = GenerationRequest(
            profile(equipment = setOf(Equipment.BODY_WEIGHT)),
            setOf(Muscle.QUADS, Muscle.LEVATOR_SCAPULAE),
        )
        val outcome = generate(request)
        assertTrue(Muscle.LEVATOR_SCAPULAE.canonical in outcome.uncoveredMuscles)
    }

    @Test
    fun `an impossible request fails rather than returning an empty plan`() {
        val outcome = generate(
            GenerationRequest(
                profile(equipment = setOf(Equipment.TIRE)),
                setOf(Muscle.PECTORALS),
            ),
        )
        assertNull("an empty plan is not a plan", outcome.plan)
        assertFalse(outcome.succeeded)
        assertTrue(outcome.rejections.isNotEmpty())
    }

    // ── Prescription ─────────────────────────────────────────────────────────

    @Test
    fun `goal sets the rep range`() {
        fun repsFor(goal: TrainingGoal) = generate(
            GenerationRequest(profile(goal = goal), setOf(Muscle.BICEPS)),
        ).plan!!.exercises.first().target

        assertEquals(5, (repsFor(TrainingGoal.STRENGTH) as com.repforth.core.model.ExerciseTarget.Reps).reps)
        assertEquals(10, (repsFor(TrainingGoal.HYPERTROPHY) as com.repforth.core.model.ExerciseTarget.Reps).reps)
        assertEquals(15, (repsFor(TrainingGoal.ENDURANCE) as com.repforth.core.model.ExerciseTarget.Reps).reps)
    }

    @Test
    fun `experience changes volume, not exercise choice`() {
        fun plan(level: ExperienceLevel) = generate(
            GenerationRequest(profile(experience = level), setOf(Muscle.BICEPS), seed = 2),
        ).plan!!

        val beginner = plan(ExperienceLevel.BEGINNER)
        val advanced = plan(ExperienceLevel.ADVANCED)
        assertTrue(
            "a beginner should not be given more sets than an advanced lifter",
            beginner.exercises.first().target.sets < advanced.exercises.first().target.sets,
        )
    }

    @Test
    fun `cardio is prescribed as time, not reps`() {
        val plan = generate(
            GenerationRequest(profile(), setOf(Muscle.CARDIOVASCULAR_SYSTEM)),
        ).plan!!
        assertTrue(plan.exercises.first().target is com.repforth.core.model.ExerciseTarget.Duration)
    }

    @Test
    fun `a generated plan says it was generated`() {
        assertEquals(PlanSource.RULES, generate(GenerationRequest(profile())).plan!!.source)
    }
}
