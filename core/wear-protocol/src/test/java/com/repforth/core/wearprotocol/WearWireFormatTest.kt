package com.repforth.core.wearprotocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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

    private fun state(phase: WearPhase = WearPhase.Exercise) = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = phase,
        exerciseId = "0025",
        exerciseName = "barbell decline wide-grip press",
        setNumber = 2,
        totalSets = 4,
        targetReps = 12,
        deadlineElapsedRealtimeMs = 90_000L,
        nextExerciseName = "dumbbell incline hammer curl",
    )

    private fun command(action: WearAction = WearAction.CompleteSet) = WearCommand(
        sessionId = "today",
        commandId = "c1",
        expectedRevision = 7,
        sentAtElapsedRealtimeMs = 1_000L,
        action = action,
    )
}
