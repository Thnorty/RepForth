@file:OptIn(ExperimentalSerializationApi::class)

package com.repforth.core.wearprotocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/*
 * The only vocabulary the phone and the watch share (§11).
 *
 * Deliberately small. §11 makes the watch a remote for a workout the phone is
 * running — it holds no history, no AI client and no key — so this carries what
 * a wrist needs to show and the six things it may ask for, and nothing else.
 *
 * **Nothing here touches Android.** That is not an accident of the current
 * contents: both a phone module and a Wear module compile against this, and a
 * `Context` reaching into the wire format is how a shared protocol stops being
 * shared. `WearProtocolIsPlatformFreeTest` is what holds that.
 */

/**
 * Bumped when a field changes meaning, not when one is added.
 *
 * Both sides send it and both sides check it. §11 pairs a non-standalone watch
 * with a phone app that can be updated separately, so the two will be different
 * versions of themselves at some point on every install — usually briefly, and
 * occasionally for weeks if one of them is not opened.
 */
const val WEAR_PROTOCOL_VERSION: Int = 1

/**
 * What the watch is showing.
 *
 * Not a copy of the phone's `SessionPhase`. That has eight values because it
 * drives a state machine; this has six because it chooses a screen, and §11
 * names the screens. `IDLE` has no member here on purpose — "no workout" is the
 * absence of a snapshot rather than a snapshot saying nothing is happening.
 */
@Serializable
enum class WearPhase {
    /** A session exists, the first set has not started. */
    Preparing,

    /** Working a set. §11's "Exercise" screen. */
    Exercise,

    /** Counting down between sets. §11's "Rest" screen. */
    Rest,

    /** Suspended by the user, from either of the two above. */
    Paused,

    /** Done, and recorded. */
    Finished,

    /**
     * Given up on, and kept distinct from [Finished].
     *
     * The phone already refuses to conflate these — §10's `ABANDONED` is
     * "terminal, and distinct from completed on purpose" — and a watch that
     * congratulated someone for abandoning a workout would be worse than one
     * that said nothing.
     */
    Abandoned,
}

/**
 * The latest snapshot, published to `/workout/active` over `DataClient` (§11).
 *
 * [revision] is the whole safety mechanism. Every command names the revision it
 * was looking at, and the phone will not apply one that was aimed at a state
 * that has since moved — see [admit].
 */
@Serializable
data class WearWorkoutState(
    /**
     * Always written, even though it has a default.
     *
     * kotlinx.serialization omits a property equal to its default unless told
     * otherwise, so this field — the one that must never be missing — was the
     * one field absent from every message. Caught by
     * `WearWireFormatTest`. `@EncodeDefault` rather than `encodeDefaults = true`
     * on a `Json` instance, because the guarantee has to hold for whichever
     * instance the phone or the watch happens to use.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val protocolVersion: Int = WEAR_PROTOCOL_VERSION,
    val sessionId: String,
    val revision: Long,
    val phase: WearPhase,
    val exerciseId: String,
    val exerciseName: String,
    val setNumber: Int,
    val totalSets: Int,
    val targetReps: Int?,
    /**
     * When the current rest ends, on the phone's `elapsedRealtime` clock.
     *
     * A deadline rather than a remaining duration, because the two devices do
     * not tick together and a duration would be stale the moment it was sent.
     * The watch subtracts its own clock and is wrong by the transfer latency
     * once, instead of drifting.
     */
    val deadlineElapsedRealtimeMs: Long?,
    val nextExerciseName: String?,
)

/**
 * What the watch may ask for. Forward-only in MVP (§11).
 *
 * One member per phone command, with nothing duplicated and nothing
 * unreachable. §11 originally listed `SkipExercise` alongside `NextExercise`;
 * they were one action under two names — the engine has a single command for
 * "leave this exercise, abandoning the sets left on it" — and having spent a
 * member on the duplicate, the set had no way to skip a single *set*, which the
 * phone has always been able to do. §11 has been corrected to match.
 */
@Serializable
enum class WearAction {
    /** Record the set as performed, to its target. */
    CompleteSet,

    /** Record the set as skipped. Still a row in the history, not an absence. */
    SkipSet,

    Pause,
    Resume,

    /** End the rest early. */
    SkipRest,

    /** Leave this exercise, abandoning whatever sets remain on it. */
    NextExercise,
}

/**
 * A request travelling the other way, over `MessageClient` (§11).
 *
 * [commandId] makes a retry harmless: the phone keeps the last several it has
 * applied and returns the current state for a repeat rather than doing it
 * twice. [expectedRevision] makes a stale request harmless in the other
 * direction — see [admit].
 */
@Serializable
data class WearCommand(
    /** Always written. See [WearWorkoutState.protocolVersion]. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val protocolVersion: Int = WEAR_PROTOCOL_VERSION,
    val sessionId: String,
    /** A UUID. Its only job is to be different every time, and the same on a retry. */
    val commandId: String,
    /** The revision of the last snapshot the watch actually saw. */
    val expectedRevision: Long,
    val sentAtElapsedRealtimeMs: Long,
    val action: WearAction,
)
