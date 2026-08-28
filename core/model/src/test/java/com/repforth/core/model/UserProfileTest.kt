package com.repforth.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exclusions are the only hard constraint in the app: §8 requires that no
 * generated plan can violate one, whether it came from the rules engine or from
 * a provider. A half-applied exclusion is worse than none, because the user
 * believes it worked and stops checking.
 */
class UserProfileTest {

    private fun profile(exclusions: Set<MovementExclusion> = emptySet()) = UserProfile(
        id = "p1",
        goal = TrainingGoal.HYPERTROPHY,
        experience = ExperienceLevel.INTERMEDIATE,
        trainingDaysPerWeek = 4,
        sessionLengthMs = 45 * 60_000L,
        availableEquipment = setOf(Equipment.DUMBBELL),
        preferredMuscles = emptySet(),
        exclusions = exclusions,
    )

    @Test
    fun `excluding a muscle excludes every upstream spelling of it`() {
        // The dataset labels the same muscle `abs` in one field and `abdominals`
        // in another. Excluding one and not the other would leave exercises the
        // user asked never to see.
        val excluded = profile(setOf(MovementExclusion(ExclusionKind.MUSCLE, "abs"))).excludedMuscles
        assertTrue(Muscle.ABS in excluded)
        assertTrue("abdominals is the same muscle under another name", Muscle.ABDOMINALS in excluded)
    }

    @Test
    fun `excluding by either spelling gives the same result`() {
        assertEquals(
            profile(setOf(MovementExclusion(ExclusionKind.MUSCLE, "quads"))).excludedMuscles,
            profile(setOf(MovementExclusion(ExclusionKind.MUSCLE, "quadriceps"))).excludedMuscles,
        )
    }

    @Test
    fun `excluding a muscle does not exclude ones merely near it`() {
        // `lower abs` sits inside the abs but is a deliberate non-merge. Widening
        // silently would remove exercises the user never asked to lose.
        val excluded = profile(setOf(MovementExclusion(ExclusionKind.MUSCLE, "abs"))).excludedMuscles
        assertTrue(Muscle.LOWER_ABS !in excluded)
        assertTrue(Muscle.OBLIQUES !in excluded)
    }

    @Test
    fun `exercise exclusions are read back as ids`() {
        val excluded = profile(setOf(MovementExclusion(ExclusionKind.EXERCISE, "0001"))).excludedExerciseIds
        assertEquals(setOf(ExerciseId("0001")), excluded)
    }

    @Test
    fun `the three exclusion kinds do not leak into each other`() {
        val mixed = profile(
            setOf(
                MovementExclusion(ExclusionKind.EXERCISE, "0001"),
                MovementExclusion(ExclusionKind.MUSCLE, "lats"),
                MovementExclusion(ExclusionKind.MOVEMENT, "overhead pressing"),
            ),
        )
        assertEquals(setOf(ExerciseId("0001")), mixed.excludedExerciseIds)
        assertTrue(Muscle.LATS in mixed.excludedMuscles)
        assertTrue("a movement name is not a muscle slug", mixed.excludedMuscles.none { it.slug == "overhead pressing" })
    }

    @Test
    fun `an unknown muscle slug is ignored rather than matching everything`() {
        // A slug written by an older version must not become a wildcard.
        assertEquals(
            emptySet<Muscle>(),
            profile(setOf(MovementExclusion(ExclusionKind.MUSCLE, "gastrocnemius"))).excludedMuscles,
        )
    }

    @Test
    fun `a profile cannot claim an impossible schedule`() {
        assertThrows(IllegalArgumentException::class.java) {
            profile().copy(trainingDaysPerWeek = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            profile().copy(trainingDaysPerWeek = 8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            profile().copy(sessionLengthMs = 0)
        }
    }

    @Test
    fun `an exclusion must name something`() {
        assertThrows(IllegalArgumentException::class.java) {
            MovementExclusion(ExclusionKind.MUSCLE, "   ")
        }
    }
}
