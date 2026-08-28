package com.repforth.core.rules

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.TrainingGoal

/**
 * Sets, reps and rest for a goal.
 *
 * These are conventional strength-training ranges, not measurements: heavy low
 * reps with long rest for strength, moderate reps with moderate rest for size,
 * light high reps with short rest for endurance. Stated as a table so the numbers
 * are visible and arguable rather than scattered through the selection code.
 *
 * **This is not medical or coaching advice**, and the app must never present it
 * as individualised programming — it is a defensible default that gets a user
 * training, which §3 requires to work with no AI configured at all.
 */
internal data class Prescription(
    val sets: Int,
    val reps: Int,
    val restMs: Long,
) {
    companion object {
        fun forGoal(goal: TrainingGoal): Prescription = when (goal) {
            TrainingGoal.STRENGTH -> Prescription(sets = 5, reps = 5, restMs = 180_000)
            TrainingGoal.HYPERTROPHY -> Prescription(sets = 4, reps = 10, restMs = 90_000)
            TrainingGoal.ENDURANCE -> Prescription(sets = 3, reps = 15, restMs = 45_000)
            TrainingGoal.GENERAL_FITNESS -> Prescription(sets = 3, reps = 12, restMs = 60_000)
        }

        /**
         * Experience changes volume, not exercise choice.
         *
         * Deliberately: gating exercises by experience would need a
         * difficulty rating the dataset does not have, so any such table would be
         * invented. Volume is a defensible axis — a beginner does fewer working
         * sets than someone with three years behind them — and it does not
         * require pretending to know that a given movement is "advanced".
         */
        fun adjustForExperience(base: Prescription, experience: ExperienceLevel): Prescription =
            when (experience) {
                ExperienceLevel.BEGINNER -> base.copy(sets = maxOf(2, base.sets - 1))
                ExperienceLevel.INTERMEDIATE -> base
                ExperienceLevel.ADVANCED -> base.copy(sets = base.sets + 1)
            }

        /** The prescription for a request: goal sets the shape, experience the volume. */
        fun adjustForExercise(request: GenerationRequest): Prescription =
            adjustForExperience(forGoal(request.profile.goal), request.profile.experience)

        /** Cardio is prescribed in time, everything else in reps. */
        fun target(candidate: ExerciseCandidate, prescription: Prescription): ExerciseTarget =
            if (candidate.isTimed) {
                ExerciseTarget.Duration(
                    sets = 1,
                    // One continuous block rather than intervals: the dataset does
                    // not say which cardio machines suit intervals, and guessing
                    // would produce plans nobody asked for.
                    durationMs = CARDIO_BLOCK_MS,
                )
            } else {
                ExerciseTarget.Reps(sets = prescription.sets, reps = prescription.reps)
            }

        private const val CARDIO_BLOCK_MS = 10 * 60_000L
    }
}
