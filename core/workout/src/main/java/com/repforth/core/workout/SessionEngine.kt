package com.repforth.core.workout

import com.repforth.core.common.time.TimeSource
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.WorkoutTemplate

/**
 * The workout state machine from §10.
 *
 * Pure apart from reading the injected clock: no database, no notification, no
 * coroutines. Callers persist what [CommandResult.Applied.events] describe, and
 * the engine never learns whether they succeeded — which is exactly why a
 * process death between the transition and the write cannot corrupt it. The
 * snapshot is the whole state; restoring is reading a row.
 *
 * Every transition here corresponds to an arrow in §10's diagram, and the tests
 * walk each one.
 */
class SessionEngine(private val time: TimeSource) {

    /** Builds a session from a plan. §10: `IDLE` to `PREPARING`. */
    fun start(sessionId: String, template: WorkoutTemplate): SessionSnapshot =
        SessionSnapshot(
            sessionId = sessionId,
            templateId = template.id,
            phase = SessionPhase.PREPARING,
            exercises = template.exercises.map { planned ->
                SessionExercise(
                    // Scoped to the session, not the plan. These become primary
                    // keys in `session_exercise`, and the plan's own row ids are
                    // the same every time it is run — so two sessions from one
                    // plan collided, and the second overwrote the first's
                    // exercises and every set attached to them. Derived rather
                    // than random so the engine stays pure and a restart of the
                    // same session id rebuilds the same rows.
                    id = "$sessionId:${planned.id}",
                    exerciseId = planned.exerciseId,
                    position = planned.position,
                    target = planned.target,
                    restMs = planned.restMs,
                )
            },
            startedAt = time.now(),
        )

    /**
     * Rebuilds the monotonic rest deadline after a process restart.
     *
     * The persisted deadline is wall-clock, because monotonic time means nothing
     * across a reboot. Converting back needs both clocks, which is why they are
     * separate: `remaining = deadline − now`, then anchored to the monotonic
     * clock so the countdown from here on is immune to the wall clock moving.
     *
     * If the wall clock jumped while the process was dead, this is wrong by
     * however far it jumped — and it is clamped to zero rather than going
     * negative, so the worst case is a rest that has already finished, not a
     * timer counting backwards.
     */
    fun restore(persisted: SessionSnapshot, deadlineAtWallClock: Long?): SessionSnapshot {
        if (persisted.phase != SessionPhase.RESTING || deadlineAtWallClock == null) return persisted
        val remaining = (deadlineAtWallClock - time.now()).coerceAtLeast(0)
        return persisted.copy(restEndsAtElapsed = time.elapsedRealtime() + remaining)
    }

    fun apply(state: SessionSnapshot, command: SessionCommand): CommandResult {
        // Idempotency first, before anything looks at the phase. A replayed
        // command must return the current state even when that state would now
        // reject it — §10 requires a duplicate to be harmless, not merely
        // detected.
        if (command.commandId in state.recentCommandIds) {
            return CommandResult.Unchanged(state, "already applied")
        }
        val expected = command.expectedRevision
        if (expected != null && expected != state.revision) {
            return CommandResult.Unchanged(
                state,
                "stale: sender expected revision $expected, current is ${state.revision}",
            )
        }
        if (state.phase.isTerminal) {
            return CommandResult.Rejected(state, "session is ${state.phase}")
        }

        return when (command) {
            is SessionCommand.Begin -> begin(state, command)
            is SessionCommand.CompleteSet -> recordSet(state, command, skipped = false)
            is SessionCommand.SkipSet -> recordSet(state, command, skipped = true)
            is SessionCommand.SkipRest -> endRest(state, command, skipped = true)
            is SessionCommand.RestElapsed -> endRest(state, command, skipped = false)
            is SessionCommand.NextExercise -> nextExercise(state, command)
            is SessionCommand.Pause -> pause(state, command)
            is SessionCommand.Resume -> resume(state, command)
            is SessionCommand.Finish -> finish(state, command)
            is SessionCommand.Abandon -> abandon(state, command)
        }
    }

    private fun begin(state: SessionSnapshot, command: SessionCommand): CommandResult {
        if (state.phase != SessionPhase.PREPARING) {
            return CommandResult.Rejected(state, "can only begin from PREPARING")
        }
        if (state.exercises.isEmpty()) {
            return CommandResult.Rejected(state, "a session needs at least one exercise")
        }
        val next = state.withCommand(command.commandId).copy(phase = SessionPhase.ACTIVE)
        return CommandResult.Applied(
            next,
            listOf(
                SessionEvent.PhaseChanged(SessionPhase.PREPARING, SessionPhase.ACTIVE),
                SessionEvent.ExerciseChanged(0),
            ),
        )
    }

    /**
     * Records the set, then either rests or ends the workout.
     *
     * §10 sends the final set straight to `COMPLETING` rather than through a rest
     * nobody waits out.
     */
    private fun recordSet(
        state: SessionSnapshot,
        command: SessionCommand,
        skipped: Boolean,
    ): CommandResult {
        if (state.phase != SessionPhase.ACTIVE) {
            return CommandResult.Rejected(state, "no set in progress")
        }
        val exercise = state.currentExercise
            ?: return CommandResult.Rejected(state, "no current exercise")

        val completed = command as? SessionCommand.CompleteSet
        val outcome = SetOutcome(
            position = state.currentSetIndex,
            skipped = skipped,
            reps = completed?.reps ?: (exercise.target as? ExerciseTarget.Reps)?.reps.takeIf { !skipped },
            weightKg = completed?.weightKg ?: exercise.target.weightKg.takeIf { !skipped },
            durationMs = completed?.durationMs
                ?: (exercise.target as? ExerciseTarget.Duration)?.durationMs.takeIf { !skipped },
            rpe = completed?.rpe,
            recordedAt = time.now(),
        )

        val withSet = state.withCommand(command.commandId).copy(
            exercises = state.exercises.mapIndexed { index, e ->
                if (index == state.currentExerciseIndex) e.copy(sets = e.sets + outcome) else e
            },
        )
        val events = mutableListOf<SessionEvent>(
            SessionEvent.SetRecorded(state.currentExerciseIndex, outcome),
        )

        if (state.isFinalSet) {
            events += SessionEvent.PhaseChanged(SessionPhase.ACTIVE, SessionPhase.COMPLETING)
            return CommandResult.Applied(withSet.copy(phase = SessionPhase.COMPLETING), events)
        }

        val restMs = exercise.restMs
        if (restMs <= 0) {
            // No rest configured: straight on, rather than a zero-length rest
            // that flickers through RESTING for one frame.
            return CommandResult.Applied(advance(withSet, events), events)
        }

        events += SessionEvent.PhaseChanged(SessionPhase.ACTIVE, SessionPhase.RESTING)
        events += SessionEvent.RestStarted(restMs, time.now() + restMs)
        return CommandResult.Applied(
            withSet.copy(
                phase = SessionPhase.RESTING,
                restEndsAtElapsed = time.elapsedRealtime() + restMs,
            ),
            events,
        )
    }

    private fun endRest(
        state: SessionSnapshot,
        command: SessionCommand,
        skipped: Boolean,
    ): CommandResult {
        if (state.phase != SessionPhase.RESTING) {
            return CommandResult.Rejected(state, "not resting")
        }
        val events = mutableListOf<SessionEvent>(SessionEvent.RestEnded(skipped))
        val cleared = state.withCommand(command.commandId).copy(restEndsAtElapsed = null)
        return CommandResult.Applied(advance(cleared, events), events)
    }

    /** Moves to the next set, or the next exercise, and back to `ACTIVE`. */
    private fun advance(
        state: SessionSnapshot,
        events: MutableList<SessionEvent>,
    ): SessionSnapshot {
        val movingToNextExercise = state.isLastSetOfExercise
        events += SessionEvent.PhaseChanged(state.phase, SessionPhase.ACTIVE)
        if (movingToNextExercise) {
            events += SessionEvent.ExerciseChanged(state.currentExerciseIndex + 1)
            return state.copy(
                phase = SessionPhase.ACTIVE,
                currentExerciseIndex = state.currentExerciseIndex + 1,
                currentSetIndex = 0,
            )
        }
        return state.copy(
            phase = SessionPhase.ACTIVE,
            currentSetIndex = state.currentSetIndex + 1,
        )
    }

    private fun nextExercise(state: SessionSnapshot, command: SessionCommand): CommandResult {
        if (state.phase != SessionPhase.ACTIVE && state.phase != SessionPhase.RESTING) {
            return CommandResult.Rejected(state, "no exercise in progress")
        }
        if (state.isLastExercise) {
            val next = state.withCommand(command.commandId).copy(
                phase = SessionPhase.COMPLETING,
                restEndsAtElapsed = null,
            )
            return CommandResult.Applied(
                next,
                listOf(SessionEvent.PhaseChanged(state.phase, SessionPhase.COMPLETING)),
            )
        }
        val next = state.withCommand(command.commandId).copy(
            phase = SessionPhase.ACTIVE,
            currentExerciseIndex = state.currentExerciseIndex + 1,
            currentSetIndex = 0,
            restEndsAtElapsed = null,
        )
        return CommandResult.Applied(
            next,
            listOf(
                SessionEvent.PhaseChanged(state.phase, SessionPhase.ACTIVE),
                SessionEvent.ExerciseChanged(next.currentExerciseIndex),
            ),
        )
    }

    /**
     * Suspends, remembering both what to return to and how much rest was left.
     *
     * The remaining rest is captured here rather than recomputed on resume,
     * because between the two the monotonic clock keeps running — a five minute
     * pause would otherwise eat five minutes of a ninety second rest.
     */
    private fun pause(state: SessionSnapshot, command: SessionCommand): CommandResult {
        if (state.phase != SessionPhase.ACTIVE && state.phase != SessionPhase.RESTING) {
            return CommandResult.Rejected(state, "nothing to pause")
        }
        val remaining = state.restEndsAtElapsed?.let {
            (it - time.elapsedRealtime()).coerceAtLeast(0)
        }
        val next = state.withCommand(command.commandId).copy(
            phase = SessionPhase.PAUSED,
            phaseBeforePause = state.phase,
            restEndsAtElapsed = null,
            restRemainingMs = remaining,
        )
        return CommandResult.Applied(
            next,
            listOf(SessionEvent.PhaseChanged(state.phase, SessionPhase.PAUSED)),
        )
    }

    private fun resume(state: SessionSnapshot, command: SessionCommand): CommandResult {
        if (state.phase != SessionPhase.PAUSED) {
            return CommandResult.Rejected(state, "not paused")
        }
        val returnTo = state.phaseBeforePause ?: SessionPhase.ACTIVE
        val next = state.withCommand(command.commandId).copy(
            phase = returnTo,
            phaseBeforePause = null,
            restRemainingMs = null,
            restEndsAtElapsed = state.restRemainingMs
                ?.let { time.elapsedRealtime() + it }
                .takeIf { returnTo == SessionPhase.RESTING },
        )
        return CommandResult.Applied(
            next,
            listOf(SessionEvent.PhaseChanged(SessionPhase.PAUSED, returnTo)),
        )
    }

    private fun finish(state: SessionSnapshot, command: SessionCommand): CommandResult {
        if (state.phase != SessionPhase.COMPLETING) {
            return CommandResult.Rejected(state, "can only finish from COMPLETING")
        }
        val next = state.withCommand(command.commandId).copy(
            phase = SessionPhase.COMPLETED,
            endedAt = time.now(),
        )
        return CommandResult.Applied(
            next,
            listOf(SessionEvent.PhaseChanged(SessionPhase.COMPLETING, SessionPhase.COMPLETED)),
        )
    }

    /**
     * Gives up, keeping everything recorded so far.
     *
     * §10 makes this reachable from every non-terminal state after `PREPARING`.
     * Abandoning is not deleting: a half-finished workout is still something the
     * user did, and discarding it would lose sets they actually performed.
     */
    private fun abandon(state: SessionSnapshot, command: SessionCommand): CommandResult {
        if (!state.phase.canAbandon && state.phase != SessionPhase.PREPARING &&
            state.phase != SessionPhase.COMPLETING
        ) {
            return CommandResult.Rejected(state, "cannot abandon from ${state.phase}")
        }
        val next = state.withCommand(command.commandId).copy(
            phase = SessionPhase.ABANDONED,
            restEndsAtElapsed = null,
            restRemainingMs = null,
            endedAt = time.now(),
        )
        return CommandResult.Applied(
            next,
            listOf(SessionEvent.PhaseChanged(state.phase, SessionPhase.ABANDONED)),
        )
    }
}
