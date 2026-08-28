package com.repforth.core.workout

import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget

/**
 * Everything about a workout in progress, as one value.
 *
 * Immutable, and complete: the engine is a function from a snapshot and a command
 * to a new snapshot, with no state held anywhere else. That is what makes
 * restoring after process death a matter of reading a row rather than replaying
 * history, and what makes every transition in §10 testable in isolation.
 */
data class SessionSnapshot(
    val sessionId: String,
    val templateId: String?,
    val phase: SessionPhase,

    /**
     * What [SessionPhase.PAUSED] suspended.
     *
     * §10 requires this: resuming has to return to `ACTIVE` or `RESTING`
     * correctly, and without it a pause during rest resumes into a set the user
     * has already done.
     */
    val phaseBeforePause: SessionPhase? = null,

    val exercises: List<SessionExercise>,
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,

    /**
     * When the current rest ends, on the **monotonic** clock.
     *
     * Never persisted: monotonic time is meaningless after a reboot. It is
     * recomputed on restore from the wall-clock deadline — see
     * [SessionEngine.restore] — which is the whole reason [com.repforth.core.common.time.TimeSource]
     * exposes two clocks.
     */
    val restEndsAtElapsed: Long? = null,

    /** Rest left at the moment of pausing. Set only while paused. */
    val restRemainingMs: Long? = null,

    /** Wall-clock start, for the record. */
    val startedAt: Long,

    val endedAt: Long? = null,

    /**
     * Increments on every applied command (§10).
     *
     * A command carrying a stale `expectedRevision` is ignored rather than
     * applied, which is what makes an out-of-order message from the watch
     * harmless instead of destructive.
     */
    val revision: Long = 0,

    /**
     * Ids of recently applied commands, newest last.
     *
     * §10 requires duplicates to return the current state rather than apply
     * twice. Revision alone nearly covers it — a replay of an applied command is
     * usually stale — but a command sent with no expected revision, or retried
     * before the first reply arrived, needs this. Bounded, because a workout is
     * long and this is not an audit log.
     */
    val recentCommandIds: List<String> = emptyList(),
) {
    val currentExercise: SessionExercise?
        get() = exercises.getOrNull(currentExerciseIndex)

    val isLastExercise: Boolean
        get() = currentExerciseIndex >= exercises.lastIndex

    val isLastSetOfExercise: Boolean
        get() = currentExercise?.let { currentSetIndex >= it.target.sets - 1 } ?: true

    /** The final set of the final exercise: the one that ends the workout. */
    val isFinalSet: Boolean
        get() = isLastExercise && isLastSetOfExercise

    /** Milliseconds of rest left, or null when not resting. */
    fun restRemaining(nowElapsed: Long): Long? = when (phase) {
        SessionPhase.RESTING -> restEndsAtElapsed?.let { (it - nowElapsed).coerceAtLeast(0) }
        SessionPhase.PAUSED -> restRemainingMs
        else -> null
    }

    internal fun withCommand(commandId: String) = copy(
        revision = revision + 1,
        recentCommandIds = (recentCommandIds + commandId).takeLast(RECENT_COMMANDS),
    )

    private companion object {
        const val RECENT_COMMANDS = 50
    }
}

/**
 * One exercise within a session.
 *
 * The target is copied from the plan at start rather than referenced, so editing
 * a plan mid-workout cannot change what the user is currently doing — and so a
 * finished session still records what it actually asked for.
 */
data class SessionExercise(
    val id: String,
    val exerciseId: ExerciseId,
    val position: Int,
    val target: ExerciseTarget,
    val restMs: Long,
    val sets: List<SetOutcome> = emptyList(),
)

/** What happened in one set. A skip is recorded, not omitted. */
data class SetOutcome(
    val position: Int,
    val skipped: Boolean,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationMs: Long? = null,
    val rpe: Int? = null,
    val recordedAt: Long,
)
