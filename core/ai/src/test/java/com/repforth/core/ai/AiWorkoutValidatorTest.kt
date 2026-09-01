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
        val response = responseWithDays(
            days = listOf(
                day(
                    index = 0,
                    title = "  Push  ",
                    exercises = listOf(
                        reps("row", order = 1, tempo = "   "),
                        reps("press", order = 0),
                    ),
                ),
            ),
            rationale = "  Balanced push and pull.  ",
        )

        val result = validator.validate(response, request(days = 1), listOf(press, row))

        assertTrue(result.isValid)
        val validatedDay = result.response!!.days.single()
        assertEquals("Push", validatedDay.title)
        assertEquals(listOf("press", "row"), validatedDay.exercises.map { it.exerciseId })
        assertEquals("Balanced push and pull.", result.response.rationale)
        assertNull(validatedDay.exercises.last().tempo)
    }

    @Test
    fun `response cannot use an id that was not offered`() {
        val result = validator.validate(
            response(listOf(reps("catalog-but-filtered", 0))),
            request(days = 1),
            listOf(press),
        )

        assertEquals(
            AiWorkoutIssue.EXERCISE_NOT_OFFERED,
            result.contractViolations.single().issue,
        )
        assertFalse(result.isValid)
    }

    @Test
    fun `duplicate ids within the same day are rejected`() {
        val result = validator.validate(
            response(listOf(reps("press", 0), reps("press", 2))),
            request(days = 1),
            listOf(press),
        )

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.DUPLICATE_EXERCISE })
        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.ORDER })
    }

    @Test
    fun `duplicate ids across different days are permitted`() {
        val response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(
                day(0, "Day 1", listOf(reps("press", 0))),
                day(1, "Day 2", listOf(reps("press", 0))),
            ),
            rationale = "Full body frequency.",
        )

        val result = validator.validate(response, request(days = 2), listOf(press))

        assertTrue(result.isValid)
        assertEquals(2, result.response!!.days.size)
    }

    @Test
    fun `day count mismatch and day index order are reported`() {
        val response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(
                day(1, "Day 2", listOf(reps("press", 0))),
            ),
            rationale = "Incomplete week.",
        )

        val result = validator.validate(response, request(days = 2), listOf(press))

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.DAY_COUNT_MISMATCH })
        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.DAY_INDEX_ORDER })
        assertFalse(result.isValid)
    }

    @Test
    fun `exactly one target shape is required`() {
        val neither = reps("press", 0).copy(repetitions = null)
        val both = reps("press", 1).copy(durationSeconds = 30)

        val result = validator.validate(response(listOf(neither, both)), request(days = 1), listOf(press))

        assertEquals(
            2,
            result.contractViolations.count { it.issue == AiWorkoutIssue.TARGET_SHAPE },
        )
    }

    @Test
    fun `numeric limits reject values the builder would otherwise clamp`() {
        val invalid = reps("press", 0).copy(
            sets = 11,
            repetitions = 0,
            restSeconds = 601,
        )

        val result = validator.validate(response(listOf(invalid)), request(days = 1), listOf(press))

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.SETS_OUT_OF_RANGE })
        assertTrue(
            result.contractViolations.any { it.issue == AiWorkoutIssue.REPETITION_OUT_OF_RANGE },
        )
        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.REST_OUT_OF_RANGE })
    }

    @Test
    fun `weight out of range is rejected`() {
        val invalid = reps("press", 0).copy(weightKg = 600.0)
        val result = validator.validate(response(listOf(invalid)), request(days = 1), listOf(press))

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.WEIGHT_OUT_OF_RANGE })
    }

    @Test
    fun `valid weight is preserved in the validated response`() {
        val valid = reps("press", 0).copy(weightKg = 45.0)
        val result = validator.validate(response(listOf(valid)), request(days = 1), listOf(press))

        assertTrue(result.isValid)
        assertEquals(45.0, result.response!!.days.single().exercises.single().weightKg ?: 0.0, 0.001)
    }

    @Test
    fun `timed and repetition candidates cannot swap target types`() {
        val result = validator.validate(
            response(listOf(reps("run", 0), duration("press", 1))),
            request(days = 1),
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
            response(listOf(reps("press", 0)), rationale = " ").copy(
                schemaVersion = AI_WORKOUT_SCHEMA_VERSION + 1,
            ),
            request(days = 1),
            listOf(press),
        )

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.SCHEMA_VERSION })
        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.RATIONALE_MISSING })
    }

    @Test
    fun `hard constraints are delegated to the rules engine`() {
        val excluded = request(
            days = 1,
            exclusions = setOf(MovementExclusion(ExclusionKind.EXERCISE, press.id.value)),
        )

        val result = validator.validate(response(listOf(reps("press", 0))), excluded, listOf(press))

        assertEquals(RejectionReason.EXCLUDED_EXERCISE, result.ruleViolations.single().reason)
        assertFalse(result.isValid)
    }

    @Test
    fun `duration ceiling uses the exact repetition target across days`() {
        val target1 = reps("press", 0).copy(
            sets = 2,
            repetitions = 10,
            restSeconds = 60,
        )
        val target2 = reps("row", 0).copy(
            sets = 2,
            repetitions = 10,
            restSeconds = 60,
        )

        val response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(
                day(0, "Day 1", listOf(target1)),
                day(1, "Day 2", listOf(target2)),
            ),
            rationale = "Split schedule",
        )

        val result = validator.validate(response, request(days = 2), listOf(press, row))

        assertTrue(result.isValid)
        assertEquals(360_000L, result.estimatedDurationMs)
    }

    @Test
    fun `a structurally valid plan still cannot exceed the session ceiling`() {
        val long = reps("press", 0).copy(
            sets = 10,
            repetitions = 100,
            restSeconds = 600,
        )

        val result = validator.validate(
            response(listOf(long)),
            request(days = 1, sessionMinutes = 5),
            listOf(press),
        )

        assertTrue(result.ruleViolations.any { it.reason == RejectionReason.NO_TIME_LEFT })
        assertFalse(result.isValid)
    }

    private fun response(
        exercises: List<AiPlannedExercise>,
        rationale: String = "A concise reason.",
    ) = AiWorkoutResponse(
        schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
        days = listOf(day(0, "Day 1", exercises)),
        rationale = rationale,
    )

    private fun responseWithDays(
        days: List<AiPlannedDay>,
        rationale: String = "A concise reason.",
    ) = AiWorkoutResponse(
        schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
        days = days,
        rationale = rationale,
    )

    private fun day(
        index: Int,
        title: String,
        exercises: List<AiPlannedExercise>,
    ) = AiPlannedDay(
        dayIndex = index,
        title = title,
        exercises = exercises,
    )

    private fun reps(
        id: String,
        order: Int,
        tempo: String? = null,
    ) = AiPlannedExercise(
        exerciseId = id,
        order = order,
        sets = 3,
        repetitions = 10,
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
        days: Int = 3,
        exclusions: Set<MovementExclusion> = emptySet(),
        sessionMinutes: Long = 60,
    ) = GenerationRequest(
        profile = UserProfile(
            id = "p",
            goal = TrainingGoal.HYPERTROPHY,
            experience = ExperienceLevel.INTERMEDIATE,
            trainingDaysPerWeek = days,
            sessionLengthMs = sessionMinutes * 60_000L,
            availableEquipment = emptySet(),
            preferredMuscles = emptySet(),
            exclusions = exclusions,
        ),
    )
}
