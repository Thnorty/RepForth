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
 *
 * The mapping is one action to one command, and total in both directions. That
 * is worth stating because it was not true when §11 was first followed
 * literally: two of its actions meant the same thing, and no action reached
 * `SkipSet`. The specification has since been corrected.
 */
fun WearCommand.toSessionCommand(): SessionCommand = when (action) {
    WearAction.CompleteSet -> SessionCommand.CompleteSet(
        commandId = commandId,
        expectedRevision = expectedRevision,
    )

    WearAction.Pause -> SessionCommand.Pause(commandId, expectedRevision)
    WearAction.Resume -> SessionCommand.Resume(commandId, expectedRevision)
    WearAction.SkipRest -> SessionCommand.SkipRest(commandId, expectedRevision)

    WearAction.SkipSet -> SessionCommand.SkipSet(commandId, expectedRevision)
}

/**
 * Whether the phone must answer a watch command itself, rather than letting the
 * ordinary state publish do it.
 *
 * **One command used to produce two publishes, and the second one was worse than
 * redundant.** `WearCommandService` published after applying a command, and
 * `WorkoutService`'s collector published again the moment the state changed — but
 * only the collector has the exercise's picture and §6's notice to attach. The
 * command service's publish therefore put out a snapshot with *no asset*, and the
 * watch does what it is told: it dropped the image, then fetched and decoded it
 * again when the collector's publish arrived a few milliseconds later. Every tap
 * on the wrist threw away the animation and re-read it over the Data Layer.
 *
 * So an accepted command publishes nothing here: changing the state is the
 * publish. What still needs answering is a command the engine *refused* — a
 * duplicate id, or a phase that does not allow it. Nothing changed, so no
 * collector will fire, and a watch left waiting would sit on a stale snapshot
 * until something else happened to move.
 *
 * [revisionAfter] is null when the workout ended underneath the command, which
 * needs no answer either: the service clears the item and the watch is told that
 * way.
 */
fun wearCommandNeedsDirectAnswer(revisionBefore: Long, revisionAfter: Long?): Boolean =
    revisionAfter != null && revisionAfter == revisionBefore
