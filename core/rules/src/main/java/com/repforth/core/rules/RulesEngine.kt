package com.repforth.core.rules

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.Muscle
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.model.WorkoutLimits
import kotlin.random.Random

/** A generated plan and the audit trail §8 asks for. */
data class GenerationOutcome(
    val plan: WorkoutTemplate?,
    val rejections: List<Rejection>,
    /** Requested muscles nothing could be found for, after every filter. */
    val uncoveredMuscles: Set<Muscle>,
) {
    val succeeded: Boolean get() = plan != null
}

/** The hard-rule boundary shared by local and provider-backed generation. */
data class CandidateFilterOutcome(
    val eligibleCandidates: List<ExerciseCandidate>,
    val rejections: List<Rejection>,
)

/**
 * Builds a workout from constraints, with no provider involved (§8).
 *
 * Pure: no database, no Android, no clock, no I/O. It takes candidates and a
 * request and returns a plan. That is what makes it exhaustively testable, and
 * it is also what lets §8's AI path validate a provider's answer against exactly
 * the rules used to generate one — [validate] and [generate] share their
 * definitions of what is allowed, rather than agreeing by convention.
 *
 * §3 requires the app to be fully useful with no AI configured. This is the
 * component that makes that true, so it is not a fallback: it is the default.
 */
class RulesEngine(
    /**
     * Supplies ids for the generated rows. Injected because a plan built twice
     * from the same seed should differ only in identity, and tests need that
     * identity to be predictable.
     */
    private val idFactory: (Int) -> String = { index -> "generated-$index" },
) {

    fun generate(
        request: GenerationRequest,
        candidates: List<ExerciseCandidate>,
        planName: String,
    ): GenerationOutcome {
        val filtered = filterCandidates(request, candidates)
        val rejections = filtered.rejections.toMutableList()
        val random = Random(request.seed)

        val wanted = request.targetMuscles.map { it.canonical }.toSet()
        val selected = select(
            filtered.eligibleCandidates,
            wanted,
            request,
            random,
            rejections,
        )

        if (selected.isEmpty()) {
            return GenerationOutcome(null, rejections, wanted)
        }

        val prescription = Prescription.adjustForExercise(request)
        val exercises = selected.mapIndexed { index, candidate ->
            PlannedExercise(
                id = idFactory(index),
                exerciseId = candidate.id,
                position = index,
                target = Prescription.target(candidate, prescription),
                restMs = prescription.restMs,
            )
        }

        val covered = selected.flatMap { c -> c.allMuscles.map { it.canonical } }.toSet()
        return GenerationOutcome(
            plan = WorkoutTemplate(
                id = idFactory(-1),
                name = planName,
                source = PlanSource.RULES,
                exercises = exercises,
            ),
            rejections = rejections,
            uncoveredMuscles = wanted - covered,
        )
    }

    /**
     * Applies every candidate-level hard constraint before either generator sees
     * the catalog (§8 steps 2–3).
     *
     * The result is sorted because SQLite does not promise row order. The AI
     * request and the rules generator must therefore receive the same stable
     * sequence, rather than each growing a subtly different filtering pass.
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
     * The hard constraints, applied before anything else (§8 step 3).
     *
     * Order matters only for the audit: a candidate excluded by id and also
     * unavailable reports the exclusion, which is the more useful fact.
     */
    private fun disqualify(
        candidate: ExerciseCandidate,
        request: GenerationRequest,
    ): RejectionReason? {
        if (candidate.id in request.excludedExerciseIds) return RejectionReason.EXCLUDED_EXERCISE

        // Any excluded muscle disqualifies, including as a secondary. Someone
        // avoiding their lower back does not want it worked incidentally.
        if (candidate.allMuscles.any { it in request.excludedMuscles }) {
            return RejectionReason.EXCLUDED_MUSCLE
        }

        // An empty equipment set means "not stated", not "has nothing" — a fresh
        // profile must not silently produce an empty catalog.
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

    /**
     * Picks exercises until the session is full.
     *
     * Coverage first, then volume: every requested muscle gets one exercise
     * before any gets a second. A plan that spends its whole hour on chest when
     * the user asked for chest and back is a worse answer than one that does
     * less of each.
     */
    private fun select(
        candidates: List<ExerciseCandidate>,
        wanted: Set<Muscle>,
        request: GenerationRequest,
        random: Random,
        rejections: MutableList<Rejection>,
    ): List<ExerciseCandidate> {
        val prescription = Prescription.adjustForExercise(request)
        val chosen = mutableListOf<ExerciseCandidate>()
        val coverage = mutableMapOf<Muscle, Int>()
        var usedMs = 0L

        // Round one: one exercise per requested muscle, hardest-to-fill first.
        // A muscle with three candidates must be served before one with sixty,
        // or the sixty consume the session and the three go uncovered.
        val byScarcity = wanted.sortedBy { muscle ->
            candidates.count { it.primarilyHits(muscle) }
        }

        for (muscle in byScarcity) {
            val pick = candidates
                .filter { it !in chosen && it.primarilyHits(muscle) }
                .ifEmpty { candidates.filter { it !in chosen && it.hits(muscle) } }
                .maxByOrNull { score(it, wanted, coverage, request, random) }
                ?: continue

            val cost = costOf(pick, prescription)
            if (usedMs + cost > request.sessionLengthMs) {
                rejections += Rejection(pick.id, RejectionReason.NO_TIME_LEFT)
                continue
            }
            chosen += pick
            usedMs += cost
            pick.allMuscles.forEach { m -> coverage.merge(m.canonical, 1, Int::plus) }
        }

        // Round two: fill the remaining time, preferring what is least covered.
        for (candidate in candidates.sortedByDescending { score(it, wanted, coverage, request, random) }) {
            if (candidate in chosen) continue
            if (chosen.size >= WorkoutLimits.maxExercises) break

            val cost = costOf(candidate, prescription)
            if (usedMs + cost > request.sessionLengthMs) {
                rejections += Rejection(candidate.id, RejectionReason.NO_TIME_LEFT)
                continue
            }
            if (candidate.allMuscles.any { (coverage[it.canonical] ?: 0) >= MAX_PER_MUSCLE }) {
                rejections += Rejection(candidate.id, RejectionReason.ENOUGH_COVERAGE)
                continue
            }
            chosen += candidate
            usedMs += cost
            candidate.allMuscles.forEach { m -> coverage.merge(m.canonical, 1, Int::plus) }
        }

        return chosen
    }

    /**
     * How well a candidate serves what is still needed.
     *
     * The random term is a tie-break only — small enough that it never outweighs
     * a real difference in coverage, large enough that two equally good
     * exercises are not always resolved the same way. Seeded, so "not always"
     * still means "reproducibly".
     */
    private fun score(
        candidate: ExerciseCandidate,
        wanted: Set<Muscle>,
        coverage: Map<Muscle, Int>,
        request: GenerationRequest,
        random: Random,
    ): Double {
        var score = 0.0
        wanted.forEach { muscle ->
            if (candidate.primarilyHits(muscle)) score += 3.0
            else if (candidate.hits(muscle)) score += 1.0
        }
        request.profile.preferredMuscles.forEach { muscle ->
            if (candidate.hits(muscle)) score += 0.5
        }
        // Push down anything already well covered, so round two spreads out.
        score -= candidate.allMuscles.sumOf { (coverage[it.canonical] ?: 0) * 0.75 }
        return score + random.nextDouble() * TIE_BREAK
    }

    private fun costOf(candidate: ExerciseCandidate, prescription: Prescription): Long =
        PlannedExercise(
            id = "estimate",
            exerciseId = candidate.id,
            position = 0,
            target = Prescription.target(candidate, prescription),
            restMs = prescription.restMs,
        ).estimatedDurationMs

    /**
     * Checks a plan against the same rules used to build one (§8).
     *
     * The reason this exists is Phase 2: a provider's answer is not trusted, and
     * "validated against the rules engine" only means something if it is *these*
     * rules rather than a second implementation that drifts.
     */
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
                // §20: no generated plan may reference an unknown exercise id.
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

    private companion object {
        /** How many exercises may touch one muscle before it is over-served. */
        const val MAX_PER_MUSCLE = 3

        const val TIE_BREAK = 0.4
    }
}
