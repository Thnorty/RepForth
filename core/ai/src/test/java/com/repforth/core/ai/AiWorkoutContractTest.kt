package com.repforth.core.ai

import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.Language
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WorkoutLimits
import com.repforth.core.rules.GenerationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkoutContractTest {

    @Test
    fun `request contains only compact normalized constraints in stable order`() {
        val profile = UserProfile(
            id = "private-profile-id",
            goal = TrainingGoal.HYPERTROPHY,
            experience = ExperienceLevel.BEGINNER,
            trainingDaysPerWeek = 3,
            sessionLengthMs = 40 * 60_000L,
            availableEquipment = setOf(Equipment.DUMBBELL, Equipment.BODY_WEIGHT),
            preferredMuscles = setOf(Muscle.TRICEPS),
            exclusions = setOf(
                MovementExclusion(ExclusionKind.MUSCLE, Muscle.CALVES.slug),
                MovementExclusion(ExclusionKind.EXERCISE, "blocked-id"),
                MovementExclusion(ExclusionKind.MOVEMENT, " overhead press "),
            ),
        )
        val request = AiWorkoutRequest.from(
            request = GenerationRequest(profile, targetMuscles = setOf(Muscle.CHEST)),
            locale = Language.TURKISH,
            eligibleCandidates = listOf(
                candidate("b", "A name the provider must not receive", Muscle.LATS),
                candidate("a", "Another private display value", Muscle.PECTORALS),
            ),
        )

        assertEquals(AI_WORKOUT_SCHEMA_VERSION, request.schemaVersion)
        assertEquals("tr", request.locale)
        assertEquals("hypertrophy", request.goal)
        assertEquals("beginner", request.experience)
        assertEquals(3, request.days)
        assertEquals(40, request.sessionDurationMinutes)
        assertEquals(WorkoutLimits.maxExercisesPerDay, request.maxExercisesPerDay)
        assertEquals(listOf("pectorals"), request.primaryMuscles)
        assertEquals(listOf("triceps"), request.secondaryMuscles)
        assertEquals(listOf("calves"), request.excludedMuscles)
        assertEquals(listOf("blocked-id"), request.excludedExerciseIds)
        assertEquals(listOf("overhead press"), request.excludedMovements)
        assertEquals(listOf("body weight", "dumbbell"), request.equipment)
        assertEquals(listOf("a", "b"), request.candidateExercises.map { it.id })

        val encoded = AiWorkoutCodec.encode(request)
        assertFalse("Profile identity must stay local", encoded.contains("private-profile-id"))
        assertFalse("Exercise names are not needed to arrange ids", encoded.contains("private display"))
        assertFalse("Instructions must never ride along by accident", encoded.contains("instructions"))
    }

    @Test
    fun `valid structured response decodes`() {
        val result = AiWorkoutCodec.decodeResponse(
            """{"schema_version":3,"days":[{"day_index":0,"title":"Push","exercises":[{"exercise_id":"a","order":0,"sets":3,"repetitions":10,"rest_seconds":60}]}],"rationale":"Balanced volume"}""",
        )

        val response = (result as AiWorkoutDecodeResult.Ok).response
        assertEquals(1, response.days.size)
        assertEquals("Push", response.days.single().title)
        assertEquals("a", response.days.single().exercises.single().exerciseId)
        assertEquals(10, response.days.single().exercises.single().repetitions)
    }

    @Test
    fun `structured response with weight_kg decodes`() {
        val result = AiWorkoutCodec.decodeResponse(
            """{"schema_version":3,"days":[{"day_index":0,"title":"Push","exercises":[{"exercise_id":"a","order":0,"sets":3,"repetitions":10,"weight_kg":25.0,"rest_seconds":60}]}],"rationale":"Balanced volume"}""",
        )

        val response = (result as AiWorkoutDecodeResult.Ok).response
        val exercise = response.days.single().exercises.single()
        assertEquals("a", exercise.exerciseId)
        assertEquals(25.0, exercise.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun `unknown fields are rejected inside the versioned contract`() {
        val result = AiWorkoutCodec.decodeResponse(
            """{"schema_version":3,"days":[],"rationale":"x","surprise":true}""",
        )

        assertEquals(AiWorkoutDecodeResult.Malformed, result)
    }

    @Test
    fun `missing required fields are rejected rather than defaulted`() {
        val result = AiWorkoutCodec.decodeResponse(
            """{"schema_version":3,"days":[]}""",
        )

        assertEquals(AiWorkoutDecodeResult.Malformed, result)
    }

    private fun candidate(id: String, name: String, muscle: Muscle) = ExerciseCandidate(
        id = ExerciseId(id),
        name = name,
        bodyPart = BodyPart.CHEST,
        target = muscle,
        muscleGroup = muscle,
        secondaryMuscles = emptySet(),
        equipment = Equipment.DUMBBELL,
    )
}
