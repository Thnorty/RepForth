package com.repforth.core.ai

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutLimits
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.rules.RulesEngine
import com.repforth.core.rules.Violation

enum class AiWorkoutIssue {
    SCHEMA_VERSION,
    DAY_COUNT_MISMATCH,
    DAY_INDEX_ORDER,
    DAY_TITLE_MISSING,
    EMPTY_PLAN,
    TOO_MANY_EXERCISES,
    ORDER,
    EXERCISE_NOT_OFFERED,
    DUPLICATE_EXERCISE,
    SETS_OUT_OF_RANGE,
    TARGET_SHAPE,
    REPETITION_OUT_OF_RANGE,
    DURATION_OUT_OF_RANGE,
    TARGET_TYPE_MISMATCH,
    REST_OUT_OF_RANGE,
    WEIGHT_OUT_OF_RANGE,
    RATIONALE_MISSING,
}

data class AiWorkoutContractViolation(
    val issue: AiWorkoutIssue,
    val dayIndex: Int? = null,
    val exerciseId: String? = null,
)

enum class AiWorkoutRetryIssueKind {
    FORMAT,
    CONTRACT,
    RULE,
}

/**
 * Compact, typed feedback for the one repair attempt allowed by §8.
 *
 * It contains codes, day indices, and exercise ids only. Rule details are
 * deliberately left out: they are local diagnostics, not provider instructions,
 * and allowing an arbitrary string through here would create a second
 * prompt-input surface.
 */
data class AiWorkoutRetryIssue(
    val kind: AiWorkoutRetryIssueKind,
    val code: String,
    val dayIndex: Int? = null,
    val exerciseId: String? = null,
)

data class AiWorkoutRetryFeedback(
    val issues: List<AiWorkoutRetryIssue>,
) {
    init {
        require(issues.isNotEmpty()) { "Retry feedback must explain what failed" }
    }

    companion object {
        val Malformed = AiWorkoutRetryFeedback(
            listOf(AiWorkoutRetryIssue(AiWorkoutRetryIssueKind.FORMAT, "malformed_response")),
        )

        fun from(result: AiWorkoutValidationResult): AiWorkoutRetryFeedback {
            require(!result.isValid) { "A valid response does not need retry feedback" }
            return AiWorkoutRetryFeedback(
                issues = buildList {
                    result.contractViolations.forEach { violation ->
                        add(
                            AiWorkoutRetryIssue(
                                kind = AiWorkoutRetryIssueKind.CONTRACT,
                                code = violation.issue.name.lowercase(),
                                dayIndex = violation.dayIndex,
                                exerciseId = violation.exerciseId,
                            ),
                        )
                    }
                    result.ruleViolations.forEach { violation ->
                        add(
                            AiWorkoutRetryIssue(
                                kind = AiWorkoutRetryIssueKind.RULE,
                                code = violation.reason.name.lowercase(),
                                exerciseId = violation.id?.value,
                            ),
                        )
                    }
                },
            )
        }
    }
}

data class AiWorkoutValidationResult(
    /** Mechanically normalised only when every check passed. */
    val response: AiWorkoutResponse?,
    val contractViolations: List<AiWorkoutContractViolation>,
    val ruleViolations: List<Violation>,
    /** Total duration estimate across all days. */
    val estimatedDurationMs: Long?,
) {
    val isValid: Boolean
        get() = response != null && contractViolations.isEmpty() && ruleViolations.isEmpty()
}

/**
 * Treats a provider response as hostile input, then delegates the product's hard
 * constraints to [RulesEngine]. Nothing here trusts a provider because it used
 * the requested schema.
 */
class AiWorkoutValidator(
    private val rules: RulesEngine = RulesEngine(),
) {
    fun validate(
        response: AiWorkoutResponse,
        request: GenerationRequest,
        offeredCandidates: List<ExerciseCandidate>,
    ): AiWorkoutValidationResult {
        val violations = mutableListOf<AiWorkoutContractViolation>()
        val offered = offeredCandidates.associateBy { it.id.value }

        if (response.schemaVersion != AI_WORKOUT_SCHEMA_VERSION) {
            violations += AiWorkoutContractViolation(AiWorkoutIssue.SCHEMA_VERSION)
        }
        if (response.rationale.isBlank()) {
            violations += AiWorkoutContractViolation(AiWorkoutIssue.RATIONALE_MISSING)
        }
        if (response.days.size != request.days) {
            violations += AiWorkoutContractViolation(AiWorkoutIssue.DAY_COUNT_MISMATCH)
        }

        val expectedDayIndices = response.days.indices.toList()
        if (response.days.map { it.dayIndex } != expectedDayIndices) {
            violations += AiWorkoutContractViolation(AiWorkoutIssue.DAY_INDEX_ORDER)
        }

        response.days.forEach { day ->
            val dayIdx = day.dayIndex
            if (day.title.isBlank()) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.DAY_TITLE_MISSING, dayIndex = dayIdx)
            }
            if (day.exercises.isEmpty()) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.EMPTY_PLAN, dayIndex = dayIdx)
            }
            if (day.exercises.size > WorkoutLimits.maxExercisesPerDay) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.TOO_MANY_EXERCISES, dayIndex = dayIdx)
            }

            val expectedOrder = day.exercises.indices.toList()
            if (day.exercises.map { it.order }.sorted() != expectedOrder) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.ORDER, dayIndex = dayIdx)
            }

            // §4.5: An exercise may repeat across days, but MUST NOT repeat within the same day.
            val seenInDay = mutableSetOf<String>()
            day.exercises.forEach { exercise ->
                val id = exercise.exerciseId
                val candidate = offered[id]
                if (candidate == null) {
                    violations += AiWorkoutContractViolation(
                        issue = AiWorkoutIssue.EXERCISE_NOT_OFFERED,
                        dayIndex = dayIdx,
                        exerciseId = id,
                    )
                }
                if (!seenInDay.add(id)) {
                    violations += AiWorkoutContractViolation(
                        issue = AiWorkoutIssue.DUPLICATE_EXERCISE,
                        dayIndex = dayIdx,
                        exerciseId = id,
                    )
                }
                if (exercise.sets !in WorkoutLimits.sets) {
                    violations += AiWorkoutContractViolation(
                        issue = AiWorkoutIssue.SETS_OUT_OF_RANGE,
                        dayIndex = dayIdx,
                        exerciseId = id,
                    )
                }
                if (exercise.restSeconds !in WorkoutLimits.restSeconds) {
                    violations += AiWorkoutContractViolation(
                        issue = AiWorkoutIssue.REST_OUT_OF_RANGE,
                        dayIndex = dayIdx,
                        exerciseId = id,
                    )
                }
                val weight = exercise.weightKg
                if (weight != null && weight !in WorkoutLimits.weightKg) {
                    violations += AiWorkoutContractViolation(
                        issue = AiWorkoutIssue.WEIGHT_OUT_OF_RANGE,
                        dayIndex = dayIdx,
                        exerciseId = id,
                    )
                }

                val repetitions = exercise.repetitions
                val duration = exercise.durationSeconds
                if ((repetitions == null) == (duration == null)) {
                    violations += AiWorkoutContractViolation(
                        issue = AiWorkoutIssue.TARGET_SHAPE,
                        dayIndex = dayIdx,
                        exerciseId = id,
                    )
                } else if (repetitions != null) {
                    if (repetitions !in WorkoutLimits.reps) {
                        violations += AiWorkoutContractViolation(
                            issue = AiWorkoutIssue.REPETITION_OUT_OF_RANGE,
                            dayIndex = dayIdx,
                            exerciseId = id,
                        )
                    }
                    if (candidate?.isTimed == true) {
                        violations += AiWorkoutContractViolation(
                            issue = AiWorkoutIssue.TARGET_TYPE_MISMATCH,
                            dayIndex = dayIdx,
                            exerciseId = id,
                        )
                    }
                } else if (duration != null) {
                    if (duration !in WorkoutLimits.durationSeconds) {
                        violations += AiWorkoutContractViolation(
                            issue = AiWorkoutIssue.DURATION_OUT_OF_RANGE,
                            dayIndex = dayIdx,
                            exerciseId = id,
                        )
                    }
                    if (candidate?.isTimed == false) {
                        violations += AiWorkoutContractViolation(
                            issue = AiWorkoutIssue.TARGET_TYPE_MISMATCH,
                            dayIndex = dayIdx,
                            exerciseId = id,
                        )
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            return AiWorkoutValidationResult(
                response = null,
                contractViolations = violations,
                ruleViolations = emptyList(),
                estimatedDurationMs = null,
            )
        }

        // Sorting a complete, unique order is a safe mechanical repair (§8).
        // Whitespace around optional text is equally mechanical.
        val normalised = response.copy(
            days = response.days
                .sortedBy { it.dayIndex }
                .map { day ->
                    day.copy(
                        title = day.title.trim(),
                        exercises = day.exercises
                            .sortedBy { it.order }
                            .map { it.copy(tempo = it.tempo?.trim()?.ifEmpty { null }) },
                    )
                },
            rationale = response.rationale.trim(),
        )

        val ruleViolations = mutableListOf<Violation>()
        var totalEstimatedDurationMs = 0L
        normalised.days.forEach { day ->
            val dayPlan = day.toValidationPlan()
            totalEstimatedDurationMs += dayPlan.estimatedDurationMs
            ruleViolations += rules.validate(
                plan = dayPlan,
                request = request,
                catalog = offeredCandidates.associateBy { it.id },
            )
        }

        return AiWorkoutValidationResult(
            response = normalised.takeIf { ruleViolations.isEmpty() },
            contractViolations = emptyList(),
            ruleViolations = ruleViolations,
            estimatedDurationMs = totalEstimatedDurationMs,
        )
    }
}

/**
 * A validation-only projection using the exact target the builder will display.
 */
private fun AiPlannedDay.toValidationPlan() = WorkoutTemplate(
    id = "ai-validation-day-$dayIndex",
    name = title.ifBlank { "Day ${dayIndex + 1}" },
    source = PlanSource.AI,
    exercises = exercises.mapIndexed { index, exercise ->
        PlannedExercise(
            id = "ai-validation-day-$dayIndex-$index",
            exerciseId = ExerciseId(exercise.exerciseId),
            position = index,
            target = exercise.repetitions?.let { repetitions ->
                ExerciseTarget.Reps(
                    sets = exercise.sets,
                    reps = repetitions,
                    weightKg = exercise.weightKg,
                )
            } ?: ExerciseTarget.Duration(
                sets = exercise.sets,
                durationMs = requireNotNull(exercise.durationSeconds) * 1_000L,
                weightKg = exercise.weightKg,
            ),
            restMs = exercise.restSeconds * 1_000L,
        )
    },
)
