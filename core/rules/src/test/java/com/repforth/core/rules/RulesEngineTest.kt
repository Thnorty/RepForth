package com.repforth.core.rules

import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Direct checks for the local filter applied before every provider request. */
class RulesEngineTest {

    private val engine = RulesEngine()

    private fun candidate(
        id: String,
        target: Muscle,
        equipment: Equipment = Equipment.DUMBBELL,
        secondary: Set<Muscle> = emptySet(),
    ) = ExerciseCandidate(
        id = ExerciseId(id),
        name = "exercise $id",
        bodyPart = BodyPart.UPPER_ARMS,
        target = target,
        muscleGroup = target,
        secondaryMuscles = secondary,
        equipment = equipment,
    )

    private fun profile(
        equipment: Set<Equipment> = emptySet(),
        exclusions: Set<MovementExclusion> = emptySet(),
    ) = UserProfile(
        id = "p",
        goal = TrainingGoal.HYPERTROPHY,
        experience = ExperienceLevel.INTERMEDIATE,
        trainingDaysPerWeek = 4,
        sessionLengthMs = 60 * 60_000,
        availableEquipment = equipment,
        preferredMuscles = emptySet(),
        exclusions = exclusions,
    )

    private val press = candidate("press", Muscle.PECTORALS, secondary = setOf(Muscle.TRICEPS))
    private val curl = candidate("curl", Muscle.BICEPS)
    private val squat = candidate("squat", Muscle.QUADS, Equipment.BODY_WEIGHT)
    private val library = listOf(press, curl, squat)

    @Test
    fun `candidate filtering is stable and sorted`() {
        val request = GenerationRequest(profile())

        val forward = engine.filterCandidates(request, library)
        val reversed = engine.filterCandidates(request, library.reversed())

        assertEquals(forward, reversed)
        assertEquals(
            forward.eligibleCandidates.map { it.id.value }.sorted(),
            forward.eligibleCandidates.map { it.id.value },
        )
    }

    @Test
    fun `an explicitly excluded exercise is never offered to the provider`() {
        val request = GenerationRequest(
            profile(
                exclusions = setOf(MovementExclusion(ExclusionKind.EXERCISE, press.id.value)),
            ),
        )

        val outcome = engine.filterCandidates(request, library)

        assertTrue(press !in outcome.eligibleCandidates)
        assertEquals(
            RejectionReason.EXCLUDED_EXERCISE,
            outcome.rejections.single { it.id == press.id }.reason,
        )
    }

    @Test
    fun `an excluded secondary muscle removes the exercise`() {
        val request = GenerationRequest(
            profile(
                exclusions = setOf(MovementExclusion(ExclusionKind.MUSCLE, "triceps")),
            ),
        )

        val outcome = engine.filterCandidates(request, library)

        assertTrue(press !in outcome.eligibleCandidates)
        assertEquals(
            RejectionReason.EXCLUDED_MUSCLE,
            outcome.rejections.single { it.id == press.id }.reason,
        )
    }

    @Test
    fun `a muscle exclusion applies through synonyms`() {
        val abs = candidate("abs", Muscle.ABDOMINALS)
        val request = GenerationRequest(
            profile(
                exclusions = setOf(MovementExclusion(ExclusionKind.MUSCLE, "abs")),
            ),
        )

        val outcome = engine.filterCandidates(request, listOf(abs))

        assertTrue(outcome.eligibleCandidates.isEmpty())
        assertEquals(RejectionReason.EXCLUDED_MUSCLE, outcome.rejections.single().reason)
    }

    @Test
    fun `unavailable equipment is never offered to the provider`() {
        val request = GenerationRequest(profile(equipment = setOf(Equipment.BODY_WEIGHT)))

        val outcome = engine.filterCandidates(request, library)

        assertEquals(listOf(squat), outcome.eligibleCandidates)
    }

    @Test
    fun `an empty equipment set means unstated rather than no equipment`() {
        val outcome = engine.filterCandidates(GenerationRequest(profile()), library)

        assertEquals(library.size, outcome.eligibleCandidates.size)
    }

    @Test
    fun `only exercises matching the requested muscles are offered`() {
        val request = GenerationRequest(profile(), targetMuscles = setOf(Muscle.PECTORALS))

        val outcome = engine.filterCandidates(request, library)

        assertEquals(listOf(press), outcome.eligibleCandidates)
        assertTrue(
            outcome.rejections
                .filter { it.id != press.id }
                .all { it.reason == RejectionReason.WRONG_MUSCLE },
        )
    }
}
