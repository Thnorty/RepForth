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
import com.repforth.core.rules.GenerationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkoutContractTest {

    @Test
    fun `request carries the constraints the local filter cannot already have applied`() {
        val request = AiWorkoutRequest.from(
            request = GenerationRequest(profile(), targetMuscles = setOf(Muscle.CHEST)),
            locale = Language.TURKISH,
            eligibleCandidates = listOf(
                candidate("b", "dumbbell lat pullover", Muscle.LATS),
                candidate("a", "dumbbell bench press", Muscle.PECTORALS),
            ),
        )

        assertEquals("tr", request.locale)
        assertEquals("hypertrophy", request.goal)
        assertEquals("beginner", request.experience)
        assertEquals(3, request.days)
        assertEquals(40, request.sessionDurationMinutes)
        assertEquals(listOf("pectorals"), request.primaryMuscles)
        assertEquals(listOf("triceps"), request.secondaryMuscles)
        assertEquals(listOf("overhead press"), request.excludedMovements)
    }

    /**
     * The filter has already removed everything these fields used to name, so
     * repeating them told the model to avoid exercises it could not see.
     */
    @Test
    fun `request does not repeat exclusions the catalog already applied`() {
        val prompt = AiWorkoutRequest.from(
            request = GenerationRequest(profile(), targetMuscles = setOf(Muscle.CHEST)),
            locale = Language.ENGLISH,
            eligibleCandidates = listOf(candidate("a", "dumbbell bench press", Muscle.PECTORALS)),
        ).toGenerationPrompt()

        assertFalse("An excluded id the model cannot see is noise", prompt.contains("blocked-id"))
        assertFalse("An excluded muscle has no candidates left", prompt.contains("calves"))
    }

    /** Candidates are grouped by muscle so a model choosing one can scan a run of them. */
    @Test
    fun `candidates are ordered by muscle then name`() {
        val request = AiWorkoutRequest.from(
            request = GenerationRequest(profile()),
            locale = Language.ENGLISH,
            eligibleCandidates = listOf(
                candidate("c", "dumbbell fly", Muscle.PECTORALS),
                candidate("b", "dumbbell lat pullover", Muscle.LATS),
                candidate("a", "dumbbell bench press", Muscle.PECTORALS),
            ),
        )

        assertEquals(listOf("b", "a", "c"), request.candidates.map { it.id })
    }

    @Test
    fun `a candidate row names the exercise and what it works`() {
        val candidate = AiExerciseCandidate.from(
            ExerciseCandidate(
                id = ExerciseId("0025"),
                name = "barbell bench press",
                bodyPart = BodyPart.CHEST,
                target = Muscle.PECTORALS,
                muscleGroup = Muscle.CHEST,
                secondaryMuscles = setOf(Muscle.TRICEPS, Muscle.DELTS, Muscle.CHEST),
                equipment = Equipment.BARBELL,
            ),
        )

        // `chest` is dropped: it canonicalises to the target the row already names.
        assertEquals(
            "0025|barbell bench press|pectorals|delts,triceps|barbell|R",
            candidate.toCatalogRow(),
        )
    }

    @Test
    fun `a timed candidate is marked so the model knows which measure to use`() {
        val candidate = AiExerciseCandidate.from(
            ExerciseCandidate(
                id = ExerciseId("1234"),
                name = "stationary bike run",
                bodyPart = BodyPart.CARDIO,
                target = Muscle.CARDIOVASCULAR_SYSTEM,
                muscleGroup = Muscle.CARDIOVASCULAR_SYSTEM,
                secondaryMuscles = emptySet(),
                equipment = Equipment.STATIONARY_BIKE,
            ),
        )

        assertTrue(candidate.timed)
        assertTrue(candidate.toCatalogRow().endsWith("|T"))
    }

    /**
     * Names are public catalog data and the ids identify them exactly, so
     * sending them discloses nothing. Who the user is still must not travel.
     */
    @Test
    fun `the prompt names exercises but never the user`() {
        val prompt = AiWorkoutRequest.from(
            request = GenerationRequest(profile(), targetMuscles = setOf(Muscle.CHEST)),
            locale = Language.ENGLISH,
            eligibleCandidates = listOf(candidate("a", "dumbbell bench press", Muscle.PECTORALS)),
        ).toGenerationPrompt()

        assertTrue(prompt.contains("dumbbell bench press"))
        assertFalse("Profile identity must stay local", prompt.contains("private-profile-id"))
        assertFalse("Instructions must never ride along by accident", prompt.contains("instructions"))
    }

    @Test
    fun `valid structured response decodes`() {
        val result = AiWorkoutCodec.decodeResponse(
            """{"days":[{"title":"Push","focus_muscles":["pectorals"],""" +
                """"exercises":[{"exercise_id":"a","sets":3,"repetitions":10,"rest_seconds":60}]}],""" +
                """"rationale":"Balanced volume"}""",
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
            """{"days":[{"title":"Push","exercises":[{"exercise_id":"a","sets":3,""" +
                """"repetitions":10,"weight_kg":25.0,"rest_seconds":60}]}],"rationale":"Balanced"}""",
        )

        val response = (result as AiWorkoutDecodeResult.Ok).response
        val exercise = response.days.single().exercises.single()
        assertEquals("a", exercise.exerciseId)
        assertEquals(25.0, exercise.weightKg ?: 0.0, 0.001)
    }

    /** The removed fields are removed on the way in too: v3 output is not v4 output. */
    @Test
    fun `a response carrying the retired fields is rejected`() {
        val result = AiWorkoutCodec.decodeResponse(
            """{"schema_version":3,"days":[{"day_index":0,"title":"Push","exercises":[]}],""" +
                """"rationale":"x"}""",
        )

        assertEquals(AiWorkoutDecodeResult.Malformed, result)
    }

    @Test
    fun `unknown fields are rejected inside the versioned contract`() {
        val result = AiWorkoutCodec.decodeResponse(
            """{"days":[],"rationale":"x","surprise":true}""",
        )

        assertEquals(AiWorkoutDecodeResult.Malformed, result)
    }

    @Test
    fun `missing required fields are rejected rather than defaulted`() {
        assertEquals(AiWorkoutDecodeResult.Malformed, AiWorkoutCodec.decodeResponse("""{"days":[]}"""))
    }

    private fun profile() = UserProfile(
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
