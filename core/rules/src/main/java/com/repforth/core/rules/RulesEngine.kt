package com.repforth.core.rules

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.WorkoutTemplate

/** The local hard-rule boundary shared by provider input and output validation. */
data class CandidateFilterOutcome(
    val eligibleCandidates: List<ExerciseCandidate>,
    val rejections: List<Rejection>,
)

/**
 * Applies the user's hard constraints before and after AI generation (§8).
 *
 * This class deliberately does not build workouts. Coach requires a configured
 * provider; the local responsibility is to restrict what leaves the phone and
 * reject any answer that violates the catalog, profile, or session ceiling.
 */
class RulesEngine {

    /**
     * Applies every candidate-level hard constraint before the provider sees
     * the catalog (§8 steps 2–3).
     *
     * The result is sorted because SQLite does not promise row order. Provider
     * requests must remain stable rather than depending on database iteration.
     */
    fun filterCandidates(
        request: GenerationRequest,
        candidates: List<ExerciseCandidate>,
    ): CandidateFilterOutcome {
        val rejections = mutableListOf<Rejection>()
        val eligible = candidates.filter { candidate ->
            val reason = disqualify(candidate, request)
            if (reason != null) rejections += Rejection(candidate.id, reason)
            reason == null
        }
        return CandidateFilterOutcome(
            eligibleCandidates = eligible.sortedBy { it.id.value },
            rejections = rejections,
        )
    }

    /**
     * Order matters only for the audit: an explicitly excluded candidate is
     * reported as excluded even when another constraint would also reject it.
     */
    private fun disqualify(
        candidate: ExerciseCandidate,
        request: GenerationRequest,
    ): RejectionReason? {
        if (candidate.id in request.excludedExerciseIds) return RejectionReason.EXCLUDED_EXERCISE

        if (candidate.allMuscles.any { it in request.excludedMuscles }) {
            return RejectionReason.EXCLUDED_MUSCLE
        }

        // An empty equipment set means "not stated", not "has nothing".
        val available = request.availableEquipment
        if (available.isNotEmpty() && candidate.equipment !in available) {
            return RejectionReason.EQUIPMENT_UNAVAILABLE
        }

        val wanted = request.targetMuscles
        if (wanted.isNotEmpty() && wanted.none { candidate.hits(it) }) {
            return RejectionReason.WRONG_MUSCLE
        }

        return null
    }

    /** Checks a provider plan against the same hard constraints used before the call. */
    fun validate(
        plan: WorkoutTemplate,
        request: GenerationRequest,
        catalog: Map<ExerciseId, ExerciseCandidate>,
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val seen = mutableSetOf<ExerciseId>()

        plan.exercises.forEach { planned ->
            val candidate = catalog[planned.exerciseId]
            if (candidate == null) {
                violations += Violation(
                    planned.exerciseId,
                    RejectionReason.WRONG_MUSCLE,
                    "no such exercise in the catalog",
                )
                return@forEach
            }
            if (!seen.add(planned.exerciseId)) {
                violations += Violation(
                    planned.exerciseId,
                    RejectionReason.ENOUGH_COVERAGE,
                    "the same exercise appears twice",
                )
            }
            disqualify(candidate, request)?.let { reason ->
                violations += Violation(planned.exerciseId, reason, "violates a hard constraint")
            }
        }

        if (plan.estimatedDurationMs > request.sessionLengthMs) {
            violations += Violation(
                null,
                RejectionReason.NO_TIME_LEFT,
                "plan runs ${plan.estimatedDurationMs / 60_000} minutes, " +
                    "ceiling is ${request.sessionLengthMs / 60_000}",
            )
        }

        return violations
    }
}
