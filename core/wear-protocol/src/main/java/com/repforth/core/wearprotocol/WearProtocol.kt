@file:OptIn(ExperimentalSerializationApi::class)

package com.repforth.core.wearprotocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
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
 * The Data Layer paths the phone and the watch agree on (§11).
 *
 * Here rather than as a constant on each side. They were four literals across two
 * files — `/workout/active` and `/workout/command`, spelled out in the phone's
 * bridge and again in the watch's store — and two strings that must be equal, in
 * two modules that never see each other, are a typo away from a watch that
 * publishes into silence. Nothing would fail to compile and no test would notice;
 * the symptom is a workout that simply never reaches the wrist.
 *
 * [PREFIX] is the part the manifests have to repeat, because an intent filter
 * cannot read a Kotlin constant. `WearPathsTest` reads both manifests and
 * asserts they still cover everything declared here — a path added outside the
 * prefix is delivered to nobody, silently.
 */
object WearPaths {

    /**
     * What both listener services filter on.
     *
     * A prefix rather than three exact filters, so a new path needs no manifest
     * change on either side. The services check the exact path themselves.
     */
    const val PREFIX: String = "/workout"

    /** The latest snapshot, over `DataClient`. Phone to watch. */
    const val STATE: String = "$PREFIX/active"

    /** A request from the wrist, over `MessageClient`. Watch to phone. */
    const val COMMAND: String = "$PREFIX/command"

    /** A timer reaching zero, over `MessageClient`. Phone to watch. */
    const val ALERT: String = "$PREFIX/alert"

    /** Every path, for the guard that checks the manifests cover them. */
    val all: List<String> = listOf(STATE, COMMAND, ALERT)
}

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
     * How long a timed set is prescribed to run, or null when it is not timed.
     *
     * The other half of §3's "repetitions **or duration**". Exactly one of this
     * and [targetReps] is set, because a phone-side `ExerciseTarget` is one or
     * the other — and before this the watch had only the reps half, so a plank
     * arrived on the wrist as a set number with nothing to say what to do.
     *
     * Kept even while [setDeadlineElapsedRealtimeMs] is counting: the deadline
     * says how much is left and this says what it was, and the watch needs the
     * second one the moment the first is absent (paused, or not yet armed).
     */
    val targetDurationMs: Long?,
    /**
     * When the current rest ends, on **the phone's** `elapsedRealtime` clock.
     *
     * A deadline rather than a remaining duration, so the countdown does not
     * need a message per second and keeps running while out of range.
     *
     * **It is meaningless on its own.** `elapsedRealtime` counts from each
     * device's own boot, so the phone's number and the watch's number share no
     * origin — subtracting one from the other yields the difference in how long
     * the two devices have been switched on. That is not a subtle error: on the
     * first hardware test it made a 60-second rest display as **591092**,
     * because the phone had been up 595515 seconds and the watch 4465.
     *
     * [publishedAtElapsedRealtimeMs] is what makes it usable. See [restRemainingMs].
     *
     * **The wire name is the old one, and stays.** The property was `deadline…`
     * while a rest was the only clock the watch knew about; a second clock made
     * that ambiguous to read, but renaming the *key* would hide the rest
     * countdown from every watch built before this — which is precisely the
     * split-version window [protocolVersion] exists for. `WearWireFormatTest`
     * asserts the key rather than the property for that reason.
     */
    @SerialName("deadlineElapsedRealtimeMs")
    val restDeadlineElapsedRealtimeMs: Long?,

    /**
     * When the timed set in progress ends, on the same clock, or null.
     *
     * Null for an exercise measured in repetitions, and null while a timed set
     * is paused or has not been armed yet — [targetDurationMs] is what the watch
     * shows then.
     *
     * A second field rather than one deadline the phase disambiguates. A rest
     * and a set never run at once, so one field would have been sufficient and
     * would also have been the kind of sufficiency that breaks silently: the
     * watch decides what to draw from the phase, and any disagreement between
     * the phase and the meaning of a shared number is a countdown labelled as
     * the wrong thing. Two names cannot be misread.
     */
    val setDeadlineElapsedRealtimeMs: Long?,

    /**
     * The phone's `elapsedRealtime` at the moment this snapshot was published.
     *
     * The reference point for both deadlines above. All three are on the
     * phone's clock, so a *difference* between them is a duration, and a
     * duration means the same thing on both devices. The watch never compares a
     * phone timestamp with one of its own.
     */
    val publishedAtElapsedRealtimeMs: Long = 0L,
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

/**
 * Something a timer did, sent to `/workout/alert` over `MessageClient` (§11).
 *
 * §3 asks the watch for "a haptic signal when a timed set or rest reaches
 * zero", and the watch cannot work out that moment for itself. It sees phases,
 * and *both* ways out of a rest — it ran out, or the user skipped it — are the
 * same phase change. The phone is the only side that knows which happened,
 * because only the phone has the events.
 *
 * **A message, not a field on the snapshot, and the reason is the one
 * `WearBridge` already gives for the opposite choice.** State goes over
 * `DataClient` because the Data Layer keeps the last value, so a watch that was
 * out of range still learns the current set. An alert wants exactly the reverse:
 * it happened at an instant, and a wrist buzzing for a rest that ended while the
 * watch was in a drawer is worse than one that never buzzed. A message has no
 * memory, which here is the feature.
 *
 * Sending it at all is gated on the phone's haptics preference, since §12 makes
 * haptics optional and the watch has no settings of its own to read.
 */
@Serializable
enum class WearAlert {
    /** A rest ran out. Never sent for a rest the user skipped — they know. */
    RestEnded,

    /** A timed set ran its full length, and was therefore recorded. */
    TimedSetEnded,
}

/** The envelope for a [WearAlert], carrying the two fields every message carries. */
@Serializable
data class WearAlertMessage(
    /** Always written. See [WearWorkoutState.protocolVersion]. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val protocolVersion: Int = WEAR_PROTOCOL_VERSION,
    /**
     * The workout this is about.
     *
     * Not used to refuse anything today — there is nothing to refuse, a buzz
     * mutates nothing — but a watch that has already moved on to the next
     * session should not be buzzed by the tail of the last one.
     */
    val sessionId: String,
    val alert: WearAlert,
)

/**
 * Whether this alert is about the workout the watch is showing.
 *
 * A buzz mutates nothing, so there is no `admit`-style refusal here and no
 * revision to check — a haptic cannot be applied to the wrong state, only at the
 * wrong moment. What it *can* be wrong about is which workout and which format:
 *
 * - A **different session** means the watch has already moved on, or never saw
 *   this one. Buzzing then is a wrist twitching for a workout its wearer is not
 *   doing.
 * - An **unreadable version** is refused for the same reason [admit] refuses a
 *   command: a field whose meaning changed is indistinguishable from one that
 *   did not, and §11 guarantees the two apps are different versions of
 *   themselves for part of every install.
 *
 * [currentSessionId] is null when the watch holds no snapshot — a cold process,
 * or one that missed the publish. That is allowed to buzz: there is nothing to
 * contradict the phone with, and the phone only sends this during a workout it
 * is actually running.
 */
fun WearAlertMessage.appliesTo(currentSessionId: String?): Boolean =
    protocolVersion == WEAR_PROTOCOL_VERSION &&
        (currentSessionId == null || currentSessionId == sessionId)

/**
 * How much rest is left, from a snapshot and nothing else.
 *
 * The subtraction is between two of the **phone's** timestamps, which is the
 * whole point: the result is a duration, and a duration is portable. Doing it
 * the obvious way instead — the phone's deadline minus the watch's clock —
 * silently returns the difference in the two devices' uptimes, which on real
 * hardware was almost seven days.
 *
 * The answer is correct at the instant the snapshot was published and ages by
 * however long it took to arrive: about a quarter of a second over Bluetooth.
 * A watch that then counts down locally is wrong once, by that latency, rather
 * than drifting.
 */
fun WearWorkoutState.restRemainingMs(): Long? = remainingUntil(restDeadlineElapsedRealtimeMs)

/**
 * How much of the timed set is left, or null when nothing is being timed.
 *
 * The same subtraction as [restRemainingMs], through the same helper and not
 * beside it. Two copies of this arithmetic would be two chances to write the
 * one that reads the watch's own clock, and that mistake has already been made
 * once here — it is the reason this function exists as a function at all.
 */
fun WearWorkoutState.setRemainingMs(): Long? = remainingUntil(setDeadlineElapsedRealtimeMs)

private fun WearWorkoutState.remainingUntil(deadline: Long?): Long? =
    deadline?.let { (it - publishedAtElapsedRealtimeMs).coerceAtLeast(0L) }
