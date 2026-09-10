package com.repforth.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a profile refuses to be.
 *
 * Most of this class tested exclusions — muscles, movements and exercises the
 * user had ruled out, and the synonym expansion that kept "abs" and
 * "abdominals" from being half-applied. Those settings were removed on
 * 2026-09-10 and the tests went with them. What is left is the pair of
 * invariants the type still enforces.
 */
class UserProfileTest {

    private fun profile() = UserProfile(
        id = "p1",
        goal = TrainingGoal.HYPERTROPHY,
        experience = ExperienceLevel.INTERMEDIATE,
        trainingDaysPerWeek = 4,
        sessionLengthMs = 45 * 60_000L,
        availableEquipment = setOf(Equipment.DUMBBELL),
    )

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
}
