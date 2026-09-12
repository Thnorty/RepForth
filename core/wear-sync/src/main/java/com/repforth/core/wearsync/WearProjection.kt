package com.repforth.core.wearsync

import com.repforth.core.model.ExerciseTarget
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot

/**
 * What of a running workout the watch is told about (§11).
 *
 * A projection, not a copy. The phone's snapshot carries every set already
 * recorded, the ids of the last fifty commands, two clocks' worth of rest
 * bookkeeping and the whole exercise list; the watch needs one exercise, one
 * number and a deadline. Sending the snapshot itself would put a workout's
 * entire history through the Data Layer several times a minute, and would leak
 * fields the watch has no business holding — §11 is explicit that it keeps no
 * independent history.
 *
 * Names arrive separately because the snapshot does not have them: it stores
 * exercise ids, and names live in the catalog, which the watch does not have
 * either. Resolving here is what lets the watch show a name at all.
 */
fun SessionSnapshot.toWearState(
    names: Map<String, String>,
    nowElapsedRealtimeMs: Long,
): WearWorkoutState? {
    val phase = phase.toWearPhase() ?: return null
    val current = currentExercise ?: return null
    val id = current.exerciseId.value

    return WearWorkoutState(
        sessionId = sessionId,
        revision = revision,
        phase = phase,
        exerciseId = id,
        // The id is a poor label, but it is a truthful one, and a blank line on
        // a watch mid-set is worse than a number.
        exerciseName = names[id] ?: id,
        setNumber = currentSetIndex + 1,
        totalSets = current.target.sets,
        targetReps = (current.target as? ExerciseTarget.Reps)?.reps,
        // §3 asks the wrist for "repetitions or duration", and until this the
        // watch had only the first half: a plank arrived as a set number with
        // nothing under it.
        targetDurationMs = (current.target as? ExerciseTarget.Duration)?.durationMs,
        // Both deadlines and the clock they are measured against, together.
        // Either alone is useless to the watch: `elapsedRealtime` counts from
        // each device's own boot, so only the difference between two of the
        // phone's own timestamps means anything on the other side.
        //
        // Rebuilt from the phase-aware remainder rather than read off the raw
        // field, which is what makes a *paused* clock survive the crossing. The
        // phone drops its deadline on a pause and keeps a duration instead —
        // there is no deadline, because a pause has no end — so a projection
        // reading `restEndsAtElapsed` published null and the watch drew "—" over
        // a rest that was merely suspended.
        restDeadlineElapsedRealtimeMs = restRemaining(nowElapsedRealtimeMs)
            ?.let { nowElapsedRealtimeMs + it },
        setDeadlineElapsedRealtimeMs = setRemaining(nowElapsedRealtimeMs)
            ?.let { nowElapsedRealtimeMs + it },
        publishedAtElapsedRealtimeMs = nowElapsedRealtimeMs,
        nextExerciseName = nextExerciseName(names),

        // Workout-level position, for the rim arc and the controls page.
        // Counted in sets because an arc wants even steps: four exercises move
        // it in four jumps, and a five-set exercise and a two-set one would
        // advance it identically.
        setsCompleted = exercises.take(currentExerciseIndex).sumOf { it.target.sets } +
            currentSetIndex,
        setsTotal = exercises.sumOf { it.target.sets },
        exerciseNumber = currentExerciseIndex + 1,
        exerciseCount = exercises.size,

        // What the rest ring measures its remainder against. Published
        // whenever a rest clock is running, which is the only time it means
        // anything -- and null the rest of the time rather than a stale figure
        // from the exercise being worked.
        restTotalMs = current.restMs.takeIf { restRemaining(nowElapsedRealtimeMs) != null },
    )
}

/**
 * The name of whatever comes next — which is usually this exercise again.
 *
 * §11 puts this on the rest screen, the only moment it is useful: it is what
 * someone decides whether to keep resting for.
 *
 * **It named the wrong exercise until 2026-09-10.** It read
 * `exercises[currentExerciseIndex + 1]` unconditionally, so a rest between the
 * first and second set of four announced the *next exercise* — and the wrist
 * told you to expect something you would not reach for another three sets. The
 * phone has always got this right; its own preview asks `isLastSetOfExercise`
 * first, and this is the same question asked in the same order.
 *
 * Null only at the very end, where there is genuinely nothing after this.
 */
private fun SessionSnapshot.nextExerciseName(names: Map<String, String>): String? {
    val id = nextExercise()?.exerciseId?.value ?: return null
    return names[id] ?: id
}

/**
 * Whatever comes next — which is usually this exercise again.
 *
 * Null only at the very end, where there is genuinely nothing after this. In a
 * session that cannot happen: the engine sends the final set straight to
 * `COMPLETING` rather than resting after it, so every rest has something on the
 * other side of it.
 */
private fun SessionSnapshot.nextExercise() = if (isLastSetOfExercise) {
    exercises.getOrNull(currentExerciseIndex + 1)
} else {
    // Another set of what is already on the bar.
    currentExercise
}

/**
 * Which exercise's picture belongs on the wire.
 *
 * **The picture is of what the wrist is being told to expect.** During a set
 * that is the exercise being worked; during a rest it is whatever
 * [WearWorkoutState.nextExerciseName] names, which between sets is the same
 * exercise again and after the last set of one is the exercise that follows.
 *
 * It was the current exercise in both cases until now, so the last rest of an
 * exercise put the animation and the name in disagreement: the text said
 * "Next: Barbell Squat" and the media page played the press that had just
 * finished. The rest is the one moment in a workout with time to look at a
 * movement, and it was showing the movement there was no longer any reason to
 * look at.
 *
 * Resting is asked as "does a rest clock still owe something", not as a phase,
 * because a **paused** rest is still a rest — that is the same question
 * `WearViewModel` asks to choose the rest screen, and the picture has to follow
 * the screen it is on.
 *
 * Null when there is no exercise at all, which is a session with none.
 */
fun SessionSnapshot.wearMediaExerciseId(nowElapsedRealtimeMs: Long): String? {
    val resting = restRemaining(nowElapsedRealtimeMs) != null
    val shown = if (resting) nextExercise() ?: currentExercise else currentExercise
    return shown?.exerciseId?.value
}

/**
 * The phone's eight phases as the watch's six, or null for "publish nothing".
 *
 * `IDLE` returns null rather than mapping to a member: §11's "no workout" screen
 * is the absence of a snapshot, and a snapshot that says nothing is happening
 * would be a message whose only content is that there was no reason to send it.
 */
private fun SessionPhase.toWearPhase(): WearPhase? = when (this) {
    SessionPhase.IDLE -> null
    SessionPhase.PREPARING -> WearPhase.Preparing
    SessionPhase.ACTIVE -> WearPhase.Exercise
    SessionPhase.RESTING -> WearPhase.Rest
    SessionPhase.PAUSED -> WearPhase.Paused
    // Both mean "over and recorded" to a wrist. The phone keeps them apart
    // because one is still writing the summary; the watch is not waiting for it.
    SessionPhase.COMPLETING -> WearPhase.Finished
    SessionPhase.COMPLETED -> WearPhase.Finished
    SessionPhase.ABANDONED -> WearPhase.Abandoned
}
