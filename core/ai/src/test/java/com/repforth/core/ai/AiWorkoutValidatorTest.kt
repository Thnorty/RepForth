package com.repforth.core.ai

import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.rules.RejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkoutValidatorTest {
    private val validator = AiWorkoutValidator()
    private val press = candidate("press", Muscle.PECTORALS)
    private val row = candidate("row", Muscle.LATS)
    private val run = candidate("run", Muscle.CARDIOVASCULAR_SYSTEM, BodyPart.CARDIO)

    @Test
    fun `valid response is ordered and mechanically normalized`() {
        val response = response(
            exercises = listOf(
                reps("row", order = 1, tempo = "   "),
                reps("press", order = 0),
            ),
            rationale = "  Balanced push and pull.  ",
        )

        val result = validator.validate(response, request(), listOf(press, row))

        assertTrue(result.isValid)
        assertEquals(listOf("press", "row"), result.response!!.exercises.map { it.exerciseId })
        assertEquals("Balanced push and pull.", result.response.rationale)
        assertNull(result.response.exercises.last().tempo)
    }

    @Test
    fun `response cannot use an id that was not offered`() {
        val result = validator.validate(
            response(listOf(reps("catalog-but-filtered", 0))),
            request(),
            listOf(press),
        )

        assertEquals(
            AiWorkoutIssue.EXERCISE_NOT_OFFERED,
            result.contractViolations.single().issue,
        )
        assertFalse(result.isValid)
    }

    @Test
    fun `duplicate ids and a broken order are both reported`() {
        val result = validator.validate(
            response(listOf(reps("press", 0), reps("press", 2))),
            request(),
            listOf(press),
        )

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.DUPLICATE_EXERCISE })
        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.ORDER })
    }

    @Test
    fun `exactly one target shape is required`() {
        val neither = reps("press", 0).copy(repetitions = null)
        val both = reps("press", 1).copy(durationSeconds = 30)

        val result = validator.validate(response(listOf(neither, both)), request(), listOf(press))

        assertEquals(
            2,
            result.contractViolations.count { it.issue == AiWorkoutIssue.TARGET_SHAPE },
        )
    }

    @Test
    fun `numeric limits reject values the builder would otherwise clamp`() {
        val invalid = reps("press", 0).copy(
            sets = 11,
            repetitions = AiRepetitionRange(0, 101),
            restSeconds = 601,
        )

        val result = validator.validate(response(listOf(invalid)), request(), listOf(press))

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.SETS_OUT_OF_RANGE })
        assertTrue(
            result.contractViolations.any { it.issue == AiWorkoutIssue.REPETITIONS_OUT_OF_RANGE },
        )
        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.REST_OUT_OF_RANGE })
    }

    @Test
    fun `timed and repetition candidates cannot swap target types`() {
        val result = validator.validate(
            response(listOf(reps("run", 0), duration("press", 1))),
            request(),
            listOf(run, press),
        )

        assertEquals(
            2,
            result.contractViolations.count { it.issue == AiWorkoutIssue.TARGET_TYPE_MISMATCH },
        )
    }

    @Test
    fun `schema version and rationale are mandatory`() {
        val result = validator.validate(
            response(listOf(reps("press", 0)), rationale = " ").copy(schemaVersion = 2),
            request(),
            listOf(press),
        )

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.SCHEMA_VERSION })
        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.RATIONALE_MISSING })
    }

    @Test
    fun `hard constraints are delegated to the rules engine`() {
        val excluded = request(
            exclusions = setOf(MovementExclusion(ExclusionKind.EXERCISE, press.id.value)),
        )

        val result = validator.validate(response(listOf(reps("press", 0))), excluded, listOf(press))

        assertEquals(RejectionReason.EXCLUDED_EXERCISE, result.ruleViolations.single().reason)
        assertFalse(result.isValid)
    }

    @Test
    fun `duration ceiling uses the upper end of a repetition range`() {
        val wideRange = reps("press", 0).copy(
            sets = 2,
            repetitions = AiRepetitionRange(8, 12),
            restSeconds = 60,
        )

        val result = validator.validate(response(listOf(wideRange)), request(), listOf(press))

        assertTrue(result.isValid)
        assertEquals(192_000L, result.estimatedDurationMs)
    }

    @Test
    fun `a structurally valid plan still cannot exceed the session ceiling`() {
        val long = reps("press", 0).copy(
            sets = 10,
            repetitions = AiRepetitionRange(100, 100),
            restSeconds = 600,
        )

        val result = validator.validate(
            response(listOf(long)),
            request(sessionMinutes = 5),
            listOf(press),
        )

        assertTrue(result.ruleViolations.any { it.reason == RejectionReason.NO_TIME_LEFT })
        assertFalse(result.isValid)
    }

    private fun response(
        exercises: List<AiPlannedExercise>,
        rationale: String = "A concise reason.",
    ) = AiWorkoutResponse(AI_WORKOUT_SCHEMA_VERSION, exercises, rationale)

    private fun reps(
        id: String,
        order: Int,
        tempo: String? = null,
    ) = AiPlannedExercise(
        exerciseId = id,
        order = order,
        sets = 3,
        repetitions = AiRepetitionRange(8, 12),
        restSeconds = 60,
        tempo = tempo,
    )

    private fun duration(id: String, order: Int) = AiPlannedExercise(
        exerciseId = id,
        order = order,
        sets = 1,
        durationSeconds = 300,
        restSeconds = 30,
    )

    private fun candidate(
        id: String,
        muscle: Muscle,
        bodyPart: BodyPart = BodyPart.CHEST,
    ) = ExerciseCandidate(
        id = ExerciseId(id),
        name = id,
        bodyPart = bodyPart,
        target = muscle,
        muscleGroup = muscle,
        secondaryMuscles = emptySet(),
        equipment = Equipment.DUMBBELL,
    )

    private fun request(
        exclusions: Set<MovementExclusion> = emptySet(),
        sessionMinutes: Long = 60,
    ) = GenerationRequest(
        profile = UserProfile(
            id = "p",
            goal = TrainingGoal.HYPERTROPHY,
            experience = ExperienceLevel.INTERMEDIATE,
            trainingDaysPerWeek = 3,
            sessionLengthMs = sessionMinutes * 60_000L,
            availableEquipment = emptySet(),
            preferredMuscles = emptySet(),
            exclusions = exclusions,
        ),
    )
}
