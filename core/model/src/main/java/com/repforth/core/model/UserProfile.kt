package com.repforth.core.model

/**
 * What the app knows about how this person trains (§3).
 *
 * Gathered at onboarding and edited in settings. Everything here is a constraint
 * the rules engine reads, not a cosmetic preference — display settings live in
 * [UserPreferences], and the split matters: changing your theme should not change
 * what the app programmes for you.
 */
data class UserProfile(
    val id: String,
    val goal: TrainingGoal,
    val experience: ExperienceLevel,
    val trainingDaysPerWeek: Int,
    /** The ceiling a session must fit inside. Milliseconds, per §7. */
    val sessionLengthMs: Long,
    /** What the user can actually train with. Empty means "unknown", not "none". */
    val availableEquipment: Set<Equipment>,
    val preferredMuscles: Set<Muscle>,
    val exclusions: Set<MovementExclusion>,
) {
    init {
        require(trainingDaysPerWeek in 1..7) { "trainingDaysPerWeek must be 1..7" }
        require(sessionLengthMs > 0) { "A session must have a positive length" }
    }

    /** Exercise ids this user must never be programmed, whatever else matches. */
    val excludedExerciseIds: Set<ExerciseId>
        get() = exclusions.filter { it.kind == ExclusionKind.EXERCISE }
            .mapTo(mutableSetOf()) { ExerciseId(it.value) }

    /**
     * Muscles to avoid, expanded across synonyms.
     *
     * Excluding `abs` has to also exclude records upstream labels `abdominals`,
     * or the exclusion is half-applied — and a half-applied exclusion is worse
     * than none, because the user believes it worked.
     */
    val excludedMuscles: Set<Muscle>
        get() {
            val named = exclusions.filter { it.kind == ExclusionKind.MUSCLE }
                .mapNotNull { Muscle.fromSlug(it.value)?.canonical }
                .toSet()
            return Muscle.entries.filterTo(mutableSetOf()) { it.canonical in named }
        }
}

/** Why the user is training. Drives set and rep ranges in the rules engine. */
enum class TrainingGoal {
    STRENGTH,
    HYPERTROPHY,
    ENDURANCE,
    GENERAL_FITNESS,
}

/** How much the user has done before. Gates exercise complexity and volume. */
enum class ExperienceLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

/**
 * Something never to programme for this user (§7).
 *
 * A hard constraint: §8 requires that no generated plan can violate one, whether
 * it came from the rules engine or from a provider.
 */
data class MovementExclusion(
    val kind: ExclusionKind,
    /** An exercise id, a muscle slug, or a movement name, per [kind]. */
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "An exclusion must name something" }
    }
}

enum class ExclusionKind {
    /** A specific catalog exercise, by upstream id. */
    EXERCISE,

    /** A muscle, by upstream slug. Expanded across synonyms when applied. */
    MUSCLE,

    /**
     * A movement pattern the catalog does not enumerate — "overhead pressing",
     * "deep knee flexion". Free text, because the dataset has no vocabulary for
     * it and inventing one would be guessing at what a physiotherapist told the
     * user.
     */
    MOVEMENT,
}
