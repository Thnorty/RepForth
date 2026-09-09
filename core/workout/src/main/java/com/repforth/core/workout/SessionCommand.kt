package com.repforth.core.workout

/**
 * Something a user — or a watch — asks of a running workout (§10).
 *
 * Every command carries an id and an expected revision because in Phase 4 these
 * arrive over a message channel that can duplicate, delay and reorder. Building
 * that in now costs two fields; retrofitting it into a live state machine later
 * means auditing every transition for whether it is safe to apply twice.
 */
sealed interface SessionCommand {
    val commandId: String

    /**
     * The revision the sender believed it was acting on.
     *
     * Null means "apply regardless", which is right for a command originating on
     * the phone where there is no channel to be stale over. A watch always sets
     * it.
     */
    val expectedRevision: Long?

    /** Begin the first exercise. `PREPARING` to `ACTIVE`. */
    data class Begin(
        override val commandId: String,
        override val expectedRevision: Long? = null,
    ) : SessionCommand

    /**
     * Record the current set and move on.
     *
     * Carries what was actually done, which may differ from the target — that
     * difference is the point of tracking.
     */
    data class CompleteSet(
        override val commandId: String,
        val reps: Int? = null,
        val weightKg: Double? = null,
        val durationMs: Long? = null,
        val rpe: Int? = null,
        override val expectedRevision: Long? = null,
    ) : SessionCommand

    /** Record the set as skipped and move on. Still a row, not an absence. */
    data class SkipSet(
        override val commandId: String,
        override val expectedRevision: Long? = null,
    ) : SessionCommand

    /** End the rest early. */
    data class SkipRest(
        override val commandId: String,
        override val expectedRevision: Long? = null,
    ) : SessionCommand

    /**
     * The rest timer reached zero.
     *
     * A command rather than something the engine notices on its own, because the
     * engine has no clock of its own to notice with — it is a pure function, and
     * whoever is watching the clock tells it.
     */
    data class RestElapsed(
        override val commandId: String,
        override val expectedRevision: Long? = null,
    ) : SessionCommand

    /**
     * A timed set's own clock reached zero.
     *
     * The only way a timed set is completed. §3's timed work is measured, not
     * declared: a plank ends when the sixty seconds end, so there is no "Log
     * set" for one and [CompleteSet] is refused while one is running. Stopping
     * early is [SkipSet], which records a skip — a set nobody finished is not a
     * set with a shorter duration, and treating it as one would put work in the
     * history that was not done.
     *
     * A command for the same reason [RestElapsed] is: the engine is a pure
     * function with no clock, so whoever is watching one tells it.
     */
    data class SetElapsed(
        override val commandId: String,
        override val expectedRevision: Long? = null,
    ) : SessionCommand

    /** Jump to the next exercise, abandoning any sets left on this one. */
    data class NextExercise(
        override val commandId: String,
        override val expectedRevision: Long? = null,
    ) : SessionCommand

    data class Pause(
        override val commandId: String,
        override val expectedRevision: Long? = null,
    ) : SessionCommand

    data class Resume(
        override val commandId: String,
        override val expectedRevision: Long? = null,
    ) : SessionCommand

    /** Finish after the last set. `COMPLETING` to `COMPLETED`. */
    data class Finish(
        override val commandId: String,
        override val expectedRevision: Long? = null,
        /**
         * §3's workout note, written once as the workout closes.
         *
         * Carried on this command rather than set by one of its own, because a
         * note is typed a character at a time and a command per keystroke would
         * be a revision, a database write and a watch publish per letter. What
         * is lost is a note that survives the process dying between typing and
         * pressing Finish, which is a few seconds.
         *
         * Blank is null: "the user wrote nothing" and "there was nowhere to
         * write" should not be different values in the history.
         */
        val note: String? = null,

        /**
         * §3's perceived effort for the whole workout, 1-10, or null.
         *
         * Per session and not per set. `SetOutcome.rpe` and this command's
         * sibling `CompleteSet.rpe` are a different question — how hard one set
         * was — and remain unwritten; asking it eight times a workout is how it
         * stops being answered honestly.
         */
        val effort: Int? = null,
    ) : SessionCommand

    /** Give up. Everything recorded so far is kept. */
    data class Abandon(
        override val commandId: String,
        override val expectedRevision: Long? = null,
    ) : SessionCommand
}

/**
 * What applying a command did.
 *
 * `Unchanged` is deliberately not an error: §10 requires a duplicate command to
 * return the current state, and a caller that cannot tell "already done" from
 * "refused" will retry the wrong things.
 */
sealed interface CommandResult {
    val state: SessionSnapshot

    /** The command applied. [events] are the side effects to persist and announce. */
    data class Applied(
        override val state: SessionSnapshot,
        val events: List<SessionEvent>,
    ) : CommandResult

    /** A duplicate, or a stale revision. Nothing happened, and nothing is wrong. */
    data class Unchanged(
        override val state: SessionSnapshot,
        val reason: String,
    ) : CommandResult

    /** Illegal in this phase — resuming a workout that is not paused. */
    data class Rejected(
        override val state: SessionSnapshot,
        val reason: String,
    ) : CommandResult
}

/**
 * Something worth persisting or announcing.
 *
 * Returned rather than performed: the engine stays pure, and the caller decides
 * what a "set recorded" means — a database write, a notification update, a
 * message to the watch, or all three.
 */
sealed interface SessionEvent {
    data class SetRecorded(val exerciseIndex: Int, val outcome: SetOutcome) : SessionEvent
    data class RestStarted(val durationMs: Long, val endsAtWallClock: Long) : SessionEvent
    data class RestEnded(val skipped: Boolean) : SessionEvent

    /**
     * A timed set began counting, which happens on arrival rather than on a tap.
     *
     * Announced so the notification can show the countdown without the service
     * having to work out for itself which exercises are timed.
     */
    data class TimedSetStarted(val durationMs: Long, val endsAtWallClock: Long) : SessionEvent

    /**
     * A timed set ran out, and was therefore completed.
     *
     * Separate from [SetRecorded], which fires for every set however it ended.
     * This one is the moment that deserves a noise: the user is holding a plank
     * and is not looking at the phone, which is the same argument §12 makes for
     * the rest alert.
     */
    data class TimedSetEnded(val exerciseIndex: Int) : SessionEvent
    data class ExerciseChanged(val exerciseIndex: Int) : SessionEvent
    data class PhaseChanged(val from: SessionPhase, val to: SessionPhase) : SessionEvent
}
