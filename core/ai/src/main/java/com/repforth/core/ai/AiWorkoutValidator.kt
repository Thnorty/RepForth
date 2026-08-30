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
    EMPTY_PLAN,
    TOO_MANY_EXERCISES,
    ORDER,
    EXERCISE_NOT_OFFERED,
    DUPLICATE_EXERCISE,
    SETS_OUT_OF_RANGE,
    TARGET_SHAPE,
    REPETITIONS_OUT_OF_RANGE,
    DURATION_OUT_OF_RANGE,
    TARGET_TYPE_MISMATCH,
    REST_OUT_OF_RANGE,
    RATIONALE_MISSING,
}

data class AiWorkoutContractViolation(
    val issue: AiWorkoutIssue,
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
 * It contains codes and exercise ids only. Rule details are deliberately left
 * out: they are local diagnostics, not provider instructions, and allowing an
 * arbitrary string through here would create a second prompt-input surface.
 */
data class AiWorkoutRetryIssue(
    val kind: AiWorkoutRetryIssueKind,
    val code: String,
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
    /** Conservative: repetition ranges use their upper bound. */
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
        if (response.exercises.isEmpty()) {
            violations += AiWorkoutContractViolation(AiWorkoutIssue.EMPTY_PLAN)
        }
        if (response.exercises.size > WorkoutLimits.maxExercises) {
            violations += AiWorkoutContractViolation(AiWorkoutIssue.TOO_MANY_EXERCISES)
        }
        if (response.rationale.isBlank()) {
            violations += AiWorkoutContractViolation(AiWorkoutIssue.RATIONALE_MISSING)
        }

        val expectedOrder = response.exercises.indices.toList()
        if (response.exercises.map { it.order }.sorted() != expectedOrder) {
            violations += AiWorkoutContractViolation(AiWorkoutIssue.ORDER)
        }

        val seen = mutableSetOf<String>()
        response.exercises.forEach { exercise ->
            val id = exercise.exerciseId
            val candidate = offered[id]
            if (candidate == null) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.EXERCISE_NOT_OFFERED, id)
            }
            if (!seen.add(id)) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.DUPLICATE_EXERCISE, id)
            }
            if (exercise.sets !in WorkoutLimits.sets) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.SETS_OUT_OF_RANGE, id)
            }
            if (exercise.restSeconds !in WorkoutLimits.restSeconds) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.REST_OUT_OF_RANGE, id)
            }

            val repetitions = exercise.repetitions
            val duration = exercise.durationSeconds
            if ((repetitions == null) == (duration == null)) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.TARGET_SHAPE, id)
            } else if (repetitions != null) {
                if (
                    repetitions.minimum !in WorkoutLimits.reps ||
                    repetitions.maximum !in WorkoutLimits.reps ||
                    repetitions.minimum > repetitions.maximum
                ) {
                    violations += AiWorkoutContractViolation(
                        AiWorkoutIssue.REPETITIONS_OUT_OF_RANGE,
                        id,
                    )
                }
                if (candidate?.isTimed == true) {
                    violations += AiWorkoutContractViolation(AiWorkoutIssue.TARGET_TYPE_MISMATCH, id)
                }
            } else if (duration != null) {
                if (duration !in WorkoutLimits.durationSeconds) {
                    violations += AiWorkoutContractViolation(AiWorkoutIssue.DURATION_OUT_OF_RANGE, id)
                }
                if (candidate?.isTimed == false) {
                    violations += AiWorkoutContractViolation(AiWorkoutIssue.TARGET_TYPE_MISMATCH, id)
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
            exercises = response.exercises
                .sortedBy { it.order }
                .map { it.copy(tempo = it.tempo?.trim()?.ifEmpty { null }) },
            rationale = response.rationale.trim(),
        )
        val projection = normalised.toValidationPlan()
        val ruleViolations = rules.validate(
            plan = projection,
            request = request,
            catalog = offeredCandidates.associateBy { it.id },
        )
        return AiWorkoutValidationResult(
            response = normalised.takeIf { ruleViolations.isEmpty() },
            contractViolations = emptyList(),
            ruleViolations = ruleViolations,
            estimatedDurationMs = projection.estimatedDurationMs,
        )
    }
}

/**
 * A validation-only projection. The response keeps its repetition range; using
 * the upper bound here makes the session-ceiling check conservative without
 * deciding which single target the current builder should display later.
 */
private fun AiWorkoutResponse.toValidationPlan() = WorkoutTemplate(
    id = "ai-validation",
    name = "AI validation",
    source = PlanSource.AI,
    exercises = exercises.mapIndexed { index, exercise ->
        PlannedExercise(
            id = "ai-validation-$index",
            exerciseId = ExerciseId(exercise.exerciseId),
            position = index,
            target = exercise.repetitions?.let { repetitions ->
                ExerciseTarget.Reps(
                    sets = exercise.sets,
                    reps = repetitions.maximum,
                )
            } ?: ExerciseTarget.Duration(
                sets = exercise.sets,
                durationMs = requireNotNull(exercise.durationSeconds) * 1_000L,
            ),
            restMs = exercise.restSeconds * 1_000L,
        )
    },
)
