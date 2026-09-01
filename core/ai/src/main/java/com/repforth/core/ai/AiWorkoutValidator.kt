package com.repforth.core.ai

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutLimits
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.rules.RejectionReason
import com.repforth.core.rules.RulesEngine
import com.repforth.core.rules.Violation

enum class AiWorkoutIssue {
    DAY_COUNT_MISMATCH,
    DAY_TITLE_MISSING,
    EMPTY_PLAN,
    TOO_MANY_EXERCISES,
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

    /** The week uses so little of the session time that it is not what was asked for. */
    WEEK_TOO_SHORT,
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
 * One rejection, as a code for this app and a sentence for the model.
 *
 * [code] is the enum name, kept for logs and tests. [explanation] is what
 * actually goes in the prompt, because `no_time_left` is this codebase's word
 * and means nothing to anyone else — a model given only the code could not tell
 * whether it had used too many exercises, too many sets or too much rest.
 *
 * Both are authored here. Nothing the provider said is ever fed back to it,
 * which was the point of using codes originally and survives their explanation.
 */
data class AiWorkoutRetryIssue(
    val kind: AiWorkoutRetryIssueKind,
    val code: String,
    val explanation: String,
    val dayIndex: Int? = null,
    val exerciseId: String? = null,
) {
    /** The sentence with its location, as one prompt line. */
    fun describe(): String {
        val where = listOfNotNull(
            dayIndex?.let { "day ${it + 1}" },
            exerciseId?.let { "exercise $it" },
        ).joinToString(", ")
        return if (where.isEmpty()) explanation else "$where: $explanation"
    }
}

data class AiWorkoutRetryFeedback(
    val issues: List<AiWorkoutRetryIssue>,
) {
    init {
        require(issues.isNotEmpty()) { "Retry feedback must explain what failed" }
    }

    companion object {
        val Malformed = AiWorkoutRetryFeedback(
            listOf(
                AiWorkoutRetryIssue(
                    kind = AiWorkoutRetryIssueKind.FORMAT,
                    code = "malformed_response",
                    explanation = "the answer was not JSON matching the schema; return only " +
                        "that JSON object, with no extra fields and no surrounding text",
                ),
            ),
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
                                explanation = violation.issue.explain(),
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
                                explanation = violation.explain(),
                                dayIndex = violation.dayIndex,
                                exerciseId = violation.id?.value,
                            ),
                        )
                    }
                },
            )
        }
    }
}

/** The model-facing sentence for each contract failure, with the real limits in it. */
private fun AiWorkoutIssue.explain(): String = when (this) {
    AiWorkoutIssue.DAY_COUNT_MISMATCH ->
        "the days array does not hold the number of days the brief asked for"
    AiWorkoutIssue.DAY_TITLE_MISSING -> "title is empty"
    AiWorkoutIssue.EMPTY_PLAN -> "the day has no exercises"
    AiWorkoutIssue.TOO_MANY_EXERCISES ->
        "the day has more than ${WorkoutLimits.maxExercisesPerDay} exercises"
    AiWorkoutIssue.EXERCISE_NOT_OFFERED ->
        "that exercise_id is not in the catalog; copy ids exactly from it"
    AiWorkoutIssue.DUPLICATE_EXERCISE -> "that exercise appears twice in the same day"
    AiWorkoutIssue.SETS_OUT_OF_RANGE ->
        "sets must be ${WorkoutLimits.sets.first}-${WorkoutLimits.sets.last}"
    AiWorkoutIssue.TARGET_SHAPE ->
        "give exactly one of repetitions or duration_seconds and set the other to null"
    AiWorkoutIssue.REPETITION_OUT_OF_RANGE ->
        "repetitions must be ${WorkoutLimits.reps.first}-${WorkoutLimits.reps.last}"
    AiWorkoutIssue.DURATION_OUT_OF_RANGE ->
        "duration_seconds must be ${WorkoutLimits.durationSeconds.first}-" +
            "${WorkoutLimits.durationSeconds.last}"
    AiWorkoutIssue.TARGET_TYPE_MISMATCH ->
        "that catalog row's R/T marking says the other measure: R rows take repetitions, " +
            "T rows take duration_seconds"
    AiWorkoutIssue.REST_OUT_OF_RANGE ->
        "rest_seconds must be ${WorkoutLimits.restSeconds.first}-${WorkoutLimits.restSeconds.last}"
    AiWorkoutIssue.WEIGHT_OUT_OF_RANGE ->
        "weight_kg must be ${WorkoutLimits.weightKg.start}-${WorkoutLimits.weightKg.endInclusive}, " +
            "or null"
    AiWorkoutIssue.RATIONALE_MISSING -> "rationale is empty"
    AiWorkoutIssue.WEEK_TOO_SHORT ->
        "the week uses almost none of the time available; a lighter day is fine, a " +
            "whole week of them is not - add working exercises or sets"
}

/**
 * The model-facing sentence for each rule failure.
 *
 * [Violation.detail] is appended because it carries computed numbers — how long
 * the day ran against the ceiling — and every string that can appear in it is
 * written in `RulesEngine`. None of it is provider text.
 */
private fun Violation.explain(): String {
    val sentence = when (reason) {
        RejectionReason.EXCLUDED_EXERCISE -> "this person must never be given that exercise"
        RejectionReason.EXCLUDED_MUSCLE -> "that exercise works a muscle this person excludes"
        RejectionReason.EXCLUDED_MOVEMENT -> "that exercise is a movement pattern this person excludes"
        RejectionReason.EQUIPMENT_UNAVAILABLE -> "this person does not have that equipment"
        RejectionReason.WRONG_MUSCLE -> "that exercise is not in the catalog you were given"
        RejectionReason.NO_TIME_LEFT ->
            "the day runs past the time available; cut an exercise, sets, or rest"
        RejectionReason.ENOUGH_COVERAGE -> "that exercise appears twice in the same day"
    }
    return "$sentence ($detail)"
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
 *
 * Day and exercise positions are read from the arrays rather than from fields
 * the model fills in. That removed two whole classes of rejection — a plan could
 * previously fail because a model numbered its days from one — without weakening
 * anything: the array is the order, and it always was.
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

        if (response.rationale.isBlank()) {
            violations += AiWorkoutContractViolation(AiWorkoutIssue.RATIONALE_MISSING)
        }
        if (response.days.size != request.days) {
            violations += AiWorkoutContractViolation(AiWorkoutIssue.DAY_COUNT_MISMATCH)
        }

        response.days.forEachIndexed { dayIdx, day ->
            if (day.title.isBlank()) {
                violations += AiWorkoutContractViolation(
                    AiWorkoutIssue.DAY_TITLE_MISSING,
                    dayIndex = dayIdx,
                )
            }
            if (day.exercises.isEmpty()) {
                violations += AiWorkoutContractViolation(AiWorkoutIssue.EMPTY_PLAN, dayIndex = dayIdx)
            }
            if (day.exercises.size > WorkoutLimits.maxExercisesPerDay) {
                violations += AiWorkoutContractViolation(
                    AiWorkoutIssue.TOO_MANY_EXERCISES,
                    dayIndex = dayIdx,
                )
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

        // Whitespace around model-authored text is a mechanical repair (§8).
        val normalised = response.copy(
            days = response.days.map { it.copy(title = it.title.trim()) },
            rationale = response.rationale.trim(),
        )

        val ruleViolations = mutableListOf<Violation>()
        var totalEstimatedDurationMs = 0L
        normalised.days.forEachIndexed { dayIdx, day ->
            val dayPlan = day.toValidationPlan(dayIdx)
            totalEstimatedDurationMs += dayPlan.estimatedDurationMs
            ruleViolations += rules
                .validate(
                    plan = dayPlan,
                    request = request,
                    catalog = offeredCandidates.associateBy { it.id },
                )
                // Which day a rule failed on is the first thing the repair
                // attempt needs, and the rules engine validates one day at a
                // time so it cannot know. Stamped here, where it is known.
                .map { it.copy(dayIndex = dayIdx) }
        }

        // Checked here rather than beside the per-exercise rules, because it is
        // the only question that cannot be asked of one day: a light day inside
        // a seven-day week is a normal thing to programme, and a week of seven
        // light days is not the week that was asked for.
        val fillViolations = buildList {
            if (isUnderFilled(totalEstimatedDurationMs, request, offeredCandidates.size)) {
                add(AiWorkoutContractViolation(AiWorkoutIssue.WEEK_TOO_SHORT))
            }
        }

        return AiWorkoutValidationResult(
            response = normalised.takeIf { ruleViolations.isEmpty() && fillViolations.isEmpty() },
            contractViolations = fillViolations,
            ruleViolations = ruleViolations,
            estimatedDurationMs = totalEstimatedDurationMs,
        )
    }

    /**
     * Whether the week used so little of its time that it answers a different
     * question than the one asked.
     *
     * Every other number in the contract is a ceiling, and a model given only
     * ceilings minimises: a seven-day week at forty-five minutes a day came back
     * as seven eight-minute days totalling 56 minutes, breaking no rule the app
     * had. The prompt now asks for a band rather than a cap; this is what stops
     * the pathological case coming back, and what gives the repair attempt
     * something to repair.
     *
     * Set low on purpose. Deciding a day should be short is the coach's job, not
     * this function's — see [AiPlanFill.MIN_WEEK_FRACTION]. This is only meant to
     * catch a week that ignored the budget, and it should stay quiet about every
     * week that merely disagrees with it.
     *
     * [candidateCount] buys a thin catalog out of it. Someone whose filters leave
     * a handful of exercises cannot fill a long session however hard the model
     * tries, and failing their generation to make a point about volume would be
     * the app blaming the model for the user's own constraints.
     */
    private fun isUnderFilled(
        totalEstimatedMs: Long,
        request: GenerationRequest,
        candidateCount: Int,
    ): Boolean {
        if (candidateCount < WorkoutLimits.maxExercisesPerDay) return false
        val budgetMs = request.sessionLengthMs * request.days
        return totalEstimatedMs < budgetMs * AiPlanFill.MIN_WEEK_FRACTION
    }
}

/**
 * A validation-only projection using the exact target the builder will display.
 */
private fun AiPlannedDay.toValidationPlan(dayIndex: Int) = WorkoutTemplate(
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
