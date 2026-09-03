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
        // The deadline and the clock it is measured against, together. Either
        // alone is useless to the watch: `elapsedRealtime` counts from each
        // device's own boot, so only the difference between two of the phone's
        // own timestamps means anything on the other side.
        deadlineElapsedRealtimeMs = restEndsAtElapsed,
        publishedAtElapsedRealtimeMs = nowElapsedRealtimeMs,
        nextExerciseName = nextExerciseName(names),
    )
}

/**
 * The name of what comes after this exercise, or null at the end.
 *
 * §11 puts this on the rest screen, which is the only moment it is useful: it
 * is what someone decides whether to keep resting for.
 */
private fun SessionSnapshot.nextExerciseName(names: Map<String, String>): String? {
    val next = exercises.getOrNull(currentExerciseIndex + 1) ?: return null
    val id = next.exerciseId.value
    return names[id] ?: id
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
