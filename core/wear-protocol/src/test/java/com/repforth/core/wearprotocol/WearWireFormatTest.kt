package com.repforth.core.wearprotocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format survives a round trip, and tolerates the other side being newer.
 *
 * §11 pairs a non-standalone watch with a phone that updates separately, so the
 * two are different versions of themselves on every install for at least a
 * while. A format that only parses when both sides match exactly would make
 * that window a broken app rather than a slightly older one.
 */
class WearWireFormatTest {

    /**
     * `ignoreUnknownKeys` is the whole point, and is asserted below rather than
     * left as a configuration detail: it is what lets an older phone read a
     * snapshot from a newer watch instead of throwing.
     */
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a state survives a round trip`() {
        val original = state()
        assertEquals(original, json.decodeFromString<WearWorkoutState>(json.encodeToString(original)))
    }

    @Test
    fun `a command survives a round trip`() {
        val original = command()
        assertEquals(original, json.decodeFromString<WearCommand>(json.encodeToString(original)))
    }

    /** A field added by a newer build must not stop an older one reading the rest. */
    @Test
    fun `an unknown field is ignored rather than fatal`() {
        val fromANewerWatch = json.encodeToString(state())
            .removeSuffix("}") + ""","heartRateBpm":142}"""

        assertEquals(state(), json.decodeFromString<WearWorkoutState>(fromANewerWatch))
    }

    /**
     * The version rides in the payload, not alongside it.
     *
     * If it were passed separately, a message that lost its envelope would be
     * parsed as whatever the reader happened to be — which is the failure the
     * version exists to prevent.
     */
    @Test
    fun `the protocol version is part of the encoded state`() {
        assertTrue("\"protocolVersion\":$WEAR_PROTOCOL_VERSION" in json.encodeToString(state()))
    }

    @Test
    fun `the protocol version is part of the encoded command`() {
        assertTrue("\"protocolVersion\":$WEAR_PROTOCOL_VERSION" in json.encodeToString(command()))
    }

    /**
     * Enums travel by name.
     *
     * A numeric ordinal would silently change meaning the first time an action
     * was inserted rather than appended, and §11's command set is explicitly
     * expected to grow past the forward-only MVP.
     */
    @Test
    fun `actions are encoded by name, not by ordinal`() {
        assertTrue("\"SkipRest\"" in json.encodeToString(command(action = WearAction.SkipRest)))
    }

    @Test
    fun `phases are encoded by name, not by ordinal`() {
        assertTrue("\"Abandoned\"" in json.encodeToString(state(phase = WearPhase.Abandoned)))
    }

    /**
     * The rest deadline's **wire name** is the old one, and this is what holds it.
     *
     * The Kotlin property was renamed to `restDeadlineElapsedRealtimeMs` when a
     * second clock made the bare `deadline…` ambiguous to read. Renaming the JSON
     * key as well would have been invisible here and fatal in the field: §11
     * guarantees the phone and the watch are different versions of themselves for
     * part of every install, and a watch built before the rename would silently
     * lose its rest countdown — `ignoreUnknownKeys` would drop the new key and the
     * missing old one would decode as null. A countdown that shows "—" is exactly
     * the failure that reports as "sometimes the timer doesn't work".
     *
     * So the assertion is on the key, not the property. A property rename is free;
     * this is the line that says the wire is not.
     */
    @Test
    fun `the rest deadline keeps the wire name it shipped with`() {
        val encoded = json.encodeToString(state())

        assertTrue(encoded, "\"deadlineElapsedRealtimeMs\":90000" in encoded)
        assertTrue(
            "The property rename must not have reached the wire",
            "restDeadlineElapsedRealtimeMs" !in encoded,
        )
    }

    @Test
    fun `an alert survives a round trip`() {
        val original = alert()
        assertEquals(original, json.decodeFromString<WearAlertMessage>(json.encodeToString(original)))
    }

    @Test
    fun `the protocol version is part of the encoded alert`() {
        assertTrue("\"protocolVersion\":$WEAR_PROTOCOL_VERSION" in json.encodeToString(alert()))
    }

    @Test
    fun `alerts are encoded by name, not by ordinal`() {
        assertTrue("\"TimedSetEnded\"" in json.encodeToString(alert(WearAlert.TimedSetEnded)))
    }

    /**
     * An alert is about the workout on screen, or it is ignored.
     *
     * A buzz mutates nothing, so there is no revision to check and nothing to
     * refuse — but a wrist twitching for a session its wearer finished twenty
     * minutes ago is still wrong, and so is one buzzing on a payload it cannot
     * read.
     */
    @Test
    fun `an alert for the session on screen applies`() {
        assertTrue(alert().appliesTo("today"))
    }

    @Test
    fun `an alert for another session does not`() {
        assertFalse(alert().appliesTo("yesterday"))
    }

    /** A cold watch has nothing to contradict the phone with. */
    @Test
    fun `an alert applies when the watch holds no snapshot`() {
        assertTrue(alert().appliesTo(null))
    }

    @Test
    fun `an alert from an unreadable version does not apply`() {
        assertFalse(alert().copy(protocolVersion = WEAR_PROTOCOL_VERSION + 1).appliesTo("today"))
    }

    private fun state(phase: WearPhase = WearPhase.Exercise) = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = phase,
        exerciseId = "0025",
        exerciseName = "barbell decline wide-grip press",
        setNumber = 2,
        totalSets = 4,
        targetReps = 12,
        targetDurationMs = null,
        restDeadlineElapsedRealtimeMs = 90_000L,
        setDeadlineElapsedRealtimeMs = null,
        nextExerciseName = "dumbbell incline hammer curl",
    )

    private fun alert(kind: WearAlert = WearAlert.RestEnded) = WearAlertMessage(
        sessionId = "today",
        alert = kind,
    )

    private fun command(action: WearAction = WearAction.CompleteSet) = WearCommand(
        sessionId = "today",
        commandId = "c1",
        expectedRevision = 7,
        sentAtElapsedRealtimeMs = 1_000L,
        action = action,
    )
}
