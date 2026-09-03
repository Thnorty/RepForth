package com.repforth.core.wearprotocol

/**
 * Whether a watch command may be applied, and what to do when it may not.
 *
 * §11 states the rule and, unusually, states the response too:
 *
 *   "Every watch command includes the last observed revision. The phone applies
 *    it, persists the result, and publishes a newer snapshot. **If the revision
 *    is stale, the phone returns the current snapshot rather than guessing.**"
 *
 * So a refusal is not an error. There is nothing to report to the user and
 * nothing to retry: the watch simply had an old picture, and the cure is to
 * send it the current one. That is why this is not `Result` and why the
 * refusing branch names the thing to send back rather than a failure.
 *
 * This is §20's "cannot silently mutate stale state", and it is the half of
 * that clause which needs no watch to test.
 */
sealed interface WearAdmission {

    /** The command refers to the state the phone is actually in. Apply it. */
    data object Apply : WearAdmission

    /**
     * Do not apply it. Publish the current snapshot instead.
     *
     * [reason] is for a log, not for a screen. §11 gives the watch no way to
     * say "that didn't work" and no reason to: the snapshot it gets back
     * already tells it everything true.
     */
    data class AnswerWithCurrentState(val reason: WearRefusal) : WearAdmission
}

/** Why a command was not applied. */
enum class WearRefusal {
    /**
     * The watch and the phone disagree about the format.
     *
     * Refusing rather than attempting a partial read: a field whose meaning
     * changed is indistinguishable from one that did not, and §11's whole
     * reason for a version number is that the two apps update separately.
     */
    UnsupportedProtocol,

    /**
     * The command names a different workout.
     *
     * Not merely stale — wrong. A watch that missed the end of one session and
     * the start of the next would otherwise apply "complete set" to a workout
     * its wearer never looked at.
     */
    WrongSession,

    /**
     * The command was aimed at a state that has since moved.
     *
     * The ordinary case, and the one the rule exists for: the user pressed
     * complete on the phone while the watch was still showing the previous set.
     */
    StaleRevision,

    /**
     * The command is aimed at a state that does not exist yet.
     *
     * A revision ahead of the phone's own cannot have been observed, so this is
     * a watch talking to a phone whose data was restored or rolled back. Same
     * response, different cause, and worth telling apart in a log.
     */
    UnknownRevision,
}

/**
 * Decide whether [command] may be applied against [current].
 *
 * Order matters and is deliberate: format before identity before position.
 * Checking the revision first would compare numbers that mean different things
 * when the protocol version differs, and would report a version mismatch as
 * staleness — which is the kind of misleading log that costs an afternoon.
 *
 * Idempotency is **not** checked here. The phone's `SessionEngine` already
 * keeps the last several applied command ids and returns the current state for
 * a repeat, and duplicating that here would mean two places that could disagree
 * about whether something had already happened.
 */
fun admit(command: WearCommand, current: WearWorkoutState): WearAdmission = when {
    command.protocolVersion != current.protocolVersion ->
        WearAdmission.AnswerWithCurrentState(WearRefusal.UnsupportedProtocol)

    command.sessionId != current.sessionId ->
        WearAdmission.AnswerWithCurrentState(WearRefusal.WrongSession)

    command.expectedRevision < current.revision ->
        WearAdmission.AnswerWithCurrentState(WearRefusal.StaleRevision)

    command.expectedRevision > current.revision ->
        WearAdmission.AnswerWithCurrentState(WearRefusal.UnknownRevision)

    else -> WearAdmission.Apply
}
