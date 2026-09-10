package com.repforth.core.model

/**
 * What the app knows about how this person trains (§3).
 *
 * Gathered at onboarding and edited in settings. Everything here is a constraint
 * the rules engine reads, not a cosmetic preference — display settings live in
 * [UserPreferences], and the split matters: changing your theme should not change
 * what the app programmes for you.
 *
 * **Preferred muscles and movement exclusions were here and are gone**, removed
 * on 2026-09-10 as more complication than they earned. They let a user favour
 * muscles, avoid muscles, avoid a free-text movement, and exclude an exercise
 * outright — four settings, four editors, a rule in the engine, a clause in the
 * AI contract and two tables. What is left is the shape of the training: the
 * goal, the experience, the week, the session ceiling and the equipment.
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
) {
    init {
        require(trainingDaysPerWeek in 1..7) { "trainingDaysPerWeek must be 1..7" }
        require(sessionLengthMs > 0) { "A session must have a positive length" }
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

