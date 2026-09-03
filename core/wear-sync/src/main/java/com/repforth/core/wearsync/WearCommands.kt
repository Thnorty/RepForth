package com.repforth.core.wearsync

import com.repforth.core.wearprotocol.WearAction
import com.repforth.core.wearprotocol.WearCommand
import com.repforth.core.workout.SessionCommand

/**
 * A watch request as something the workout engine understands.
 *
 * The engine was built for this. `SessionCommand`'s own documentation says it
 * is "something a user — **or a watch** — asks of a running workout", and its
 * `expectedRevision` says "a watch always sets it". So this is a translation
 * between two vocabularies, not an adapter around a mismatch.
 *
 * **A watch never supplies what was actually lifted.** It has no fields to type
 * into, and §11 keeps it that way. Leaving reps, weight and duration null is
 * not a loss of information — `SessionEngine.recordSet` falls back to the
 * target, so a set completed from the wrist records "did what was planned",
 * which is exactly what the user meant by pressing it.
 */
fun WearCommand.toSessionCommand(): SessionCommand = when (action) {
    WearAction.CompleteSet -> SessionCommand.CompleteSet(
        commandId = commandId,
        expectedRevision = expectedRevision,
    )

    WearAction.Pause -> SessionCommand.Pause(commandId, expectedRevision)
    WearAction.Resume -> SessionCommand.Resume(commandId, expectedRevision)
    WearAction.SkipRest -> SessionCommand.SkipRest(commandId, expectedRevision)

    // Both of §11's remaining actions mean "leave this exercise", and the engine
    // has one command for that: NextExercise, which abandons the sets left on
    // it. The redundancy is in the specified enum rather than introduced here,
    // and is recorded in docs/PLAN.md along with what it costs — there is no
    // watch action for skipping a single *set*, which the phone can do.
    WearAction.SkipExercise -> SessionCommand.NextExercise(commandId, expectedRevision)
    WearAction.NextExercise -> SessionCommand.NextExercise(commandId, expectedRevision)
}
