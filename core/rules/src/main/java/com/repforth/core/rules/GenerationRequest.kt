package com.repforth.core.rules

import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.Muscle
import com.repforth.core.model.UserProfile

/**
 * What the user asked for, plus what they are allowed (§8).
 *
 * The profile supplies the constraints that always apply; this supplies the ones
 * for a single generation. They are kept apart because the profile is a standing
 * fact and the request is a one-off, and conflating them would make "just legs
 * today" look like a permanent change to what the user trains.
 */
data class GenerationRequest(
    val profile: UserProfile,

    /** Muscles to work. Empty means "anything my profile allows". */
    val targetMuscles: Set<Muscle> = emptySet(),

    /**
     * Overrides the profile's session ceiling for this one plan.
     *
     * Null means use the profile's. Present when someone has forty minutes today
     * rather than their usual hour.
     */
    val sessionLengthMsOverride: Long? = null,

    /**
     * Equipment available right now, if narrower than the profile's.
     *
     * A hotel gym is not a permanent change to what the user owns.
     */
    val equipmentOverride: Set<Equipment>? = null,

) {
    val sessionLengthMs: Long
        get() = sessionLengthMsOverride ?: profile.sessionLengthMs

    val availableEquipment: Set<Equipment>
        get() = equipmentOverride ?: profile.availableEquipment

    val excludedExerciseIds: Set<ExerciseId>
        get() = profile.excludedExerciseIds

    val excludedMuscles: Set<Muscle>
        get() = profile.excludedMuscles
}

/**
 * Why a candidate could not be used.
 *
 * Kept rather than discarded because §8 requires a constraint audit, and because
 * "no exercises matched" is a useless thing to tell a user who has excluded most
 * of the catalog without realising.
 */
enum class RejectionReason {
    EXCLUDED_EXERCISE,
    EXCLUDED_MUSCLE,
    EQUIPMENT_UNAVAILABLE,
    WRONG_MUSCLE,
    /** Would have pushed the plan past the session-length ceiling. */
    NO_TIME_LEFT,
    /** Its muscle already had enough coverage in this plan. */
    ENOUGH_COVERAGE,
}

/** One candidate and why it was not selected. */
data class Rejection(val id: ExerciseId, val reason: RejectionReason)

/**
 * A rule a plan breaks.
 *
 * §8 requires AI output to be validated against these rules before it reaches
 * the editable builder.
 */
data class Violation(val id: ExerciseId?, val reason: RejectionReason, val detail: String)
