package com.repforth.core.model

/**
 * An exercise as the rules engine sees it (§8).
 *
 * Everything selection needs and nothing it does not: no instructions, no media.
 * Filtering 1,324 records is done on every generation, and pulling both languages
 * of instruction text to decide whether an exercise hits the lats would read
 * 15,420 rows to answer a question about six columns.
 */
data class ExerciseCandidate(
    val id: ExerciseId,
    val name: String,
    val bodyPart: BodyPart,
    val target: Muscle,
    val muscleGroup: Muscle,
    val secondaryMuscles: Set<Muscle>,
    val equipment: Equipment,
) {
    /** Every muscle this exercise touches, in any role. */
    val allMuscles: Set<Muscle>
        get() = buildSet {
            add(target)
            add(muscleGroup)
            addAll(secondaryMuscles)
        }

    /**
     * Whether this exercise works [muscle], comparing canonically.
     *
     * The dataset names the same muscle differently across its three fields, so
     * a raw equality check would miss an exercise whose `muscle_group` is
     * `abdominals` when the user asked for `abs`.
     */
    fun hits(muscle: Muscle): Boolean =
        allMuscles.any { it.canonical == muscle.canonical }

    /** Whether [muscle] is what this exercise is primarily for, rather than assistance. */
    fun primarilyHits(muscle: Muscle): Boolean =
        target.canonical == muscle.canonical || muscleGroup.canonical == muscle.canonical

    /** Cardio is timed; everything else is counted in reps. */
    val isTimed: Boolean get() = bodyPart == BodyPart.CARDIO
}
