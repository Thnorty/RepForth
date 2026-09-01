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
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkoutValidatorTest {
    private val validator = AiWorkoutValidator()
    private val press = candidate("press", Muscle.PECTORALS)
    private val row = candidate("row", Muscle.LATS)
    private val run = candidate("run", Muscle.CARDIOVASCULAR_SYSTEM, BodyPart.CARDIO)

    /**
     * Array position is the order, so there is nothing left to sort by.
     *
     * The response used to carry an `order` field that the validator checked was
     * a permutation of the indices and then sorted on — a round trip that could
     * only restore what the array already said, and that failed a whole week of
     * generation when it did not.
     */
    @Test
    fun `valid response keeps its order and is mechanically normalized`() {
        val response = responseWithDays(
            days = listOf(day("  Push  ", listOf(reps("press"), reps("row")))),
            rationale = "  Balanced push and pull.  ",
        )

        val result = validator.validate(response, request(days = 1), listOf(press, row))

        assertTrue(result.isValid)
        val validatedDay = result.response!!.days.single()
        assertEquals("Push", validatedDay.title)
        assertEquals(listOf("press", "row"), validatedDay.exercises.map { it.exerciseId })
        assertEquals("Balanced push and pull.", result.response.rationale)
    }

    @Test
    fun `response cannot use an id that was not offered`() {
        val result = validator.validate(
            response(listOf(reps("catalog-but-filtered"))),
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
            response(listOf(reps("press"), reps("press"))),
            request(days = 1),
            listOf(press),
        )

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.DUPLICATE_EXERCISE })
    }

    @Test
    fun `duplicate ids across different days are permitted`() {
        val response = AiWorkoutResponse(
            days = listOf(
                day("Day 1", listOf(reps("press"))),
                day("Day 2", listOf(reps("press"))),
            ),
            rationale = "Full body frequency.",
        )

        val result = validator.validate(response, request(days = 2), listOf(press))

        assertTrue(result.isValid)
        assertEquals(2, result.response!!.days.size)
    }

    @Test
    fun `a week short of the days that were asked for is rejected`() {
        val response = AiWorkoutResponse(
            days = listOf(day("Day 2", listOf(reps("press")))),
            rationale = "Incomplete week.",
        )

        val result = validator.validate(response, request(days = 2), listOf(press))

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.DAY_COUNT_MISMATCH })
        assertFalse(result.isValid)
    }

    @Test
    fun `exactly one target shape is required`() {
        val neither = reps("press").copy(repetitions = null)
        val both = reps("row").copy(durationSeconds = 30)

        val result = validator.validate(
            response(listOf(neither, both)),
            request(days = 1),
            listOf(press, row),
        )

        assertEquals(
            2,
            result.contractViolations.count { it.issue == AiWorkoutIssue.TARGET_SHAPE },
        )
    }

    @Test
    fun `numeric limits reject values the builder would otherwise clamp`() {
        val invalid = reps("press").copy(sets = 11, repetitions = 0, restSeconds = 601)

        val result = validator.validate(response(listOf(invalid)), request(days = 1), listOf(press))

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.SETS_OUT_OF_RANGE })
        assertTrue(
            result.contractViolations.any { it.issue == AiWorkoutIssue.REPETITION_OUT_OF_RANGE },
        )
        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.REST_OUT_OF_RANGE })
    }

    @Test
    fun `weight out of range is rejected`() {
        val invalid = reps("press").copy(weightKg = 600.0)
        val result = validator.validate(response(listOf(invalid)), request(days = 1), listOf(press))

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.WEIGHT_OUT_OF_RANGE })
    }

    @Test
    fun `valid weight is preserved in the validated response`() {
        val valid = reps("press").copy(weightKg = 45.0)
        val result = validator.validate(response(listOf(valid)), request(days = 1), listOf(press))

        assertTrue(result.isValid)
        assertEquals(45.0, result.response!!.days.single().exercises.single().weightKg ?: 0.0, 0.001)
    }

    @Test
    fun `timed and repetition candidates cannot swap target types`() {
        val result = validator.validate(
            response(listOf(reps("run"), duration("press"))),
            request(days = 1),
            listOf(run, press),
        )

        assertEquals(
            2,
            result.contractViolations.count { it.issue == AiWorkoutIssue.TARGET_TYPE_MISMATCH },
        )
    }

    @Test
    fun `rationale is mandatory`() {
        val result = validator.validate(
            response(listOf(reps("press")), rationale = " "),
            request(days = 1),
            listOf(press),
        )

        assertTrue(result.contractViolations.any { it.issue == AiWorkoutIssue.RATIONALE_MISSING })
    }

    @Test
    fun `hard constraints are delegated to the rules engine`() {
        val excluded = request(
            days = 1,
            exclusions = setOf(MovementExclusion(ExclusionKind.EXERCISE, press.id.value)),
        )

        val result = validator.validate(response(listOf(reps("press"))), excluded, listOf(press))

        assertEquals(RejectionReason.EXCLUDED_EXERCISE, result.ruleViolations.single().reason)
        assertFalse(result.isValid)
    }

    @Test
    fun `duration ceiling uses the exact repetition target across days`() {
        val target1 = reps("press").copy(sets = 2, repetitions = 10, restSeconds = 60)
        val target2 = reps("row").copy(sets = 2, repetitions = 10, restSeconds = 60)

        val response = AiWorkoutResponse(
            days = listOf(
                day("Day 1", listOf(target1)),
                day("Day 2", listOf(target2)),
            ),
            rationale = "Split schedule",
        )

        val result = validator.validate(response, request(days = 2), listOf(press, row))

        assertTrue(result.isValid)
        assertEquals(360_000L, result.estimatedDurationMs)
    }

    @Test
    fun `a structurally valid plan still cannot exceed the session ceiling`() {
        val long = reps("press").copy(sets = 10, repetitions = 100, restSeconds = 600)

        val result = validator.validate(
            response(listOf(long)),
            request(days = 1, sessionMinutes = 5),
            listOf(press),
        )

        assertTrue(result.ruleViolations.any { it.reason == RejectionReason.NO_TIME_LEFT })
        assertFalse(result.isValid)
    }

    /**
     * A rule failure has to say which day it happened on.
     *
     * The rules engine validates a day at a time and cannot know, so the
     * validator stamps it. Without this the repair attempt is told to cut an
     * exercise from a week rather than from Tuesday.
     */
    @Test
    fun `a rule violation names the day it happened on`() {
        val fine = reps("press").copy(sets = 1, repetitions = 5, restSeconds = 30)
        val long = reps("row").copy(sets = 10, repetitions = 100, restSeconds = 600)

        val result = validator.validate(
            responseWithDays(
                listOf(day("Day 1", listOf(fine)), day("Day 2", listOf(long))),
            ),
            request(days = 2, sessionMinutes = 30),
            listOf(press, row),
        )

        val violation = result.ruleViolations.single { it.reason == RejectionReason.NO_TIME_LEFT }
        assertEquals(1, violation.dayIndex)
    }

    /**
     * Retry feedback has to be readable, because the model is the reader.
     *
     * It used to be a JSON array of this codebase's enum names. `no_time_left`
     * does not say whether the fix is fewer exercises, fewer sets or less rest.
     */
    @Test
    fun `retry feedback explains the failure in words and locates it`() {
        val result = validator.validate(
            responseWithDays(
                listOf(
                    day("Day 1", listOf(reps("press"))),
                    day("Day 2", listOf(reps("row").copy(sets = 99))),
                ),
            ),
            request(days = 2),
            listOf(press, row),
        )

        val issue = AiWorkoutRetryFeedback.from(result).issues
            .single { it.code == "sets_out_of_range" }

        assertEquals("day 2, exercise row: sets must be 1-10", issue.describe())
    }

    private fun response(
        exercises: List<AiPlannedExercise>,
        rationale: String = "A concise reason.",
    ) = AiWorkoutResponse(
        days = listOf(day("Day 1", exercises)),
        rationale = rationale,
    )

    private fun responseWithDays(
        days: List<AiPlannedDay>,
        rationale: String = "A concise reason.",
    ) = AiWorkoutResponse(days = days, rationale = rationale)

    private fun day(title: String, exercises: List<AiPlannedExercise>) = AiPlannedDay(
        title = title,
        exercises = exercises,
    )

    private fun reps(id: String) = AiPlannedExercise(
        exerciseId = id,
        sets = 3,
        repetitions = 10,
        restSeconds = 60,
    )

    private fun duration(id: String) = AiPlannedExercise(
        exerciseId = id,
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
