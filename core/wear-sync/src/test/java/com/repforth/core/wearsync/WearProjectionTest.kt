package com.repforth.core.wearsync

import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.restRemainingMs
import com.repforth.core.wearprotocol.setRemainingMs
import com.repforth.core.workout.SessionExercise
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the watch is told, and what it is deliberately not told. */
class WearProjectionTest {

    @Test
    fun `the current set and its target cross over`() {
        val state = snapshot(currentSetIndex = 1).toWearState(NAMES, PUBLISHED_AT)!!

        assertEquals(2, state.setNumber)
        assertEquals(4, state.totalSets)
        assertEquals(12, state.targetReps)
        assertEquals("barbell decline wide-grip press", state.exerciseName)
    }

    /** The revision is the safety mechanism, so it must survive the projection. */
    @Test
    fun `the revision crosses over`() {
        assertEquals(9L, snapshot(revision = 9).toWearState(NAMES, PUBLISHED_AT)!!.revision)
    }

    /**
     * A duration-based exercise has no rep target, and must say so rather than
     * inventing a number the watch would display as a goal.
     */
    @Test
    fun `a timed exercise reports no rep target`() {
        val timed = snapshot(
            target = ExerciseTarget.Duration(sets = 3, durationMs = 45_000L),
        )
        assertNull(timed.toWearState(NAMES, PUBLISHED_AT)!!.targetReps)
    }

    /**
     * The other half of §3's "repetitions **or** duration".
     *
     * The pair with the test above is the point: exactly one of the two targets
     * is ever set, and it is how the watch decides which screen it is drawing. A
     * projection that filled in both, or neither, would give the wrist a plank
     * with a rep count or a curl with a countdown.
     */
    @Test
    fun `a timed exercise reports how long it is`() {
        val timed = snapshot(
            target = ExerciseTarget.Duration(sets = 3, durationMs = 45_000L),
        ).toWearState(NAMES, PUBLISHED_AT)!!

        assertEquals(45_000L, timed.targetDurationMs)
    }

    @Test
    fun `an exercise counted in reps has no duration`() {
        assertNull(snapshot().toWearState(NAMES, PUBLISHED_AT)!!.targetDurationMs)
    }

    @Test
    fun `the rest deadline crosses over as a duration from the publish clock`() {
        val resting = snapshot(
            phase = SessionPhase.RESTING,
            restEndsAtElapsed = PUBLISHED_AT + 90_000L,
        ).toWearState(NAMES, PUBLISHED_AT)!!

        assertEquals(90_000L, resting.restRemainingMs())
        assertNull("A rest is not a set", resting.setRemainingMs())
    }

    /**
     * A running timed set gets its own deadline, and the rest field stays empty.
     *
     * Two fields rather than one the phase disambiguates, so this pair of
     * assertions is the contract: the watch reads the one named for what it is
     * drawing, and a projection that wrote the set's clock into the rest field
     * would put a countdown on the rest screen for a set nobody is resting from.
     */
    @Test
    fun `a running timed set publishes its own deadline`() {
        val holding = snapshot(
            target = ExerciseTarget.Duration(sets = 3, durationMs = 60_000L),
            setEndsAtElapsed = PUBLISHED_AT + 42_000L,
        ).toWearState(NAMES, PUBLISHED_AT)!!

        assertEquals(42_000L, holding.setRemainingMs())
        assertNull("Nothing is resting", holding.restRemainingMs())
    }

    @Test
    fun `an exercise counted in reps has no set deadline`() {
        assertNull(snapshot().toWearState(NAMES, PUBLISHED_AT)!!.setRemainingMs())
    }

    /**
     * A paused clock survives the crossing, which it did not before.
     *
     * The phone drops its deadline on a pause and keeps a duration instead —
     * there is no deadline, because a pause has no end. A projection reading the
     * raw `restEndsAtElapsed` therefore published null, and the watch drew "—"
     * over a rest that was merely suspended. Rebuilding from the phase-aware
     * remainder is what fixes it, and this is the assertion that keeps it fixed.
     */
    @Test
    fun `a paused rest still says how much is owed`() {
        val paused = snapshot(
            phase = SessionPhase.PAUSED,
            restRemainingMs = 25_000L,
        ).toWearState(NAMES, PUBLISHED_AT)!!

        assertEquals(25_000L, paused.restRemainingMs())
    }

    @Test
    fun `a paused timed set still says how much is owed`() {
        val paused = snapshot(
            phase = SessionPhase.PAUSED,
            target = ExerciseTarget.Duration(sets = 3, durationMs = 60_000L),
            setRemainingMs = 40_000L,
        ).toWearState(NAMES, PUBLISHED_AT)!!

        assertEquals(40_000L, paused.setRemainingMs())
        assertNull("Only the clock that was running is owed anything", paused.restRemainingMs())
    }

    /**
     * A deadline belonging to a phase the watch is not in must not be sent.
     *
     * The raw fields can hold a number that the phase says is over, so reading
     * them directly would publish a set countdown to a watch showing a rest
     * screen — two clocks running at once on a device that has room for one.
     */
    @Test
    fun `a deadline from another phase is not published`() {
        val resting = snapshot(
            phase = SessionPhase.RESTING,
            restEndsAtElapsed = PUBLISHED_AT + 90_000L,
            setEndsAtElapsed = PUBLISHED_AT + 42_000L,
        ).toWearState(NAMES, PUBLISHED_AT)!!

        assertEquals(90_000L, resting.restRemainingMs())
        assertNull("The set's clock stopped when the rest started", resting.setRemainingMs())
    }

    /**
     * **"Next" is usually this exercise again, and the watch said otherwise.**
     *
     * Reported from a wrist: resting after the first of four sets, the watch
     * named the *next exercise*. This projection read
     * `exercises[currentExerciseIndex + 1]` whatever the set index was, so the
     * rest screen promised something three sets away.
     *
     * The old test asserted exactly that and passed, which is the part worth
     * keeping in view: it was written from the code rather than from what the
     * screen is for. The phone has always asked `isLastSetOfExercise` first.
     */
    @Test
    fun `with sets remaining, next is the same exercise`() {
        // Set 1 of 4 on exercise one.
        assertEquals(
            "barbell decline wide-grip press",
            snapshot().toWearState(NAMES, PUBLISHED_AT)!!.nextExerciseName,
        )
    }

    @Test
    fun `on the last set, next is the next exercise`() {
        // Set 4 of 4, so the rest after it leads somewhere else.
        val lastSet = snapshot(currentSetIndex = 3)
        assertEquals(
            "dumbbell incline hammer curl",
            lastSet.toWearState(NAMES, PUBLISHED_AT)!!.nextExerciseName,
        )
    }

    /** The final set of the final exercise: there is genuinely nothing after. */
    @Test
    fun `the last set of the last exercise has nothing after it`() {
        val onTheLast = snapshot(currentExerciseIndex = 1, currentSetIndex = 2)
        assertNull(onTheLast.toWearState(NAMES, PUBLISHED_AT)!!.nextExerciseName)
    }

    /** And mid-exercise on the last exercise still points at itself. */
    @Test
    fun `the last exercise still names itself while it has sets left`() {
        val midway = snapshot(currentExerciseIndex = 1, currentSetIndex = 0)
        assertEquals(
            "dumbbell incline hammer curl",
            midway.toWearState(NAMES, PUBLISHED_AT)!!.nextExerciseName,
        )
    }

    /**
     * An id the catalog cannot name is shown as the id.
     *
     * A blank line on a watch mid-set is worse than a number, and the id is at
     * least true.
     */
    @Test
    fun `an unresolvable name falls back to the id`() {
        assertEquals("0025", snapshot().toWearState(emptyMap(), PUBLISHED_AT)!!.exerciseName)
    }

    /** §11's "no workout" screen is the absence of a snapshot, not a quiet one. */
    @Test
    fun `an idle session publishes nothing`() {
        assertNull(snapshot(phase = SessionPhase.IDLE).toWearState(NAMES, PUBLISHED_AT))
    }

    @Test
    fun `a session with no exercises publishes nothing`() {
        assertNull(snapshot(exercises = emptyList()).toWearState(NAMES, PUBLISHED_AT))
    }

    @Test
    fun `every phase the watch can be in maps to one of its own`() {
        val mapped = mapOf(
            SessionPhase.PREPARING to WearPhase.Preparing,
            SessionPhase.ACTIVE to WearPhase.Exercise,
            SessionPhase.RESTING to WearPhase.Rest,
            SessionPhase.PAUSED to WearPhase.Paused,
            SessionPhase.COMPLETING to WearPhase.Finished,
            SessionPhase.COMPLETED to WearPhase.Finished,
            SessionPhase.ABANDONED to WearPhase.Abandoned,
        )

        val actual = mapped.keys.associateWith { snapshot(phase = it).toWearState(NAMES, PUBLISHED_AT)?.phase }
        assertEquals(mapped, actual)
    }

    /**
     * Abandoning is not finishing, and the watch is told which.
     *
     * The phone keeps these apart deliberately (§10: "terminal, and distinct
     * from completed on purpose"), and a watch congratulating someone for
     * giving up would throw that away at the last step.
     */
    @Test
    fun `an abandoned workout is not reported as finished`() {
        val abandoned = snapshot(phase = SessionPhase.ABANDONED).toWearState(NAMES, PUBLISHED_AT)!!
        assertEquals(WearPhase.Abandoned, abandoned.phase)
    }

    /**
     * The rest deadline is measured against the clock reading published with it,
     * so the projection has to carry both — see `restRemainingMs`.
     */
    @Test
    fun `the publish timestamp travels with the deadline`() {
        val resting = snapshot(
            phase = SessionPhase.RESTING,
            restEndsAtElapsed = PUBLISHED_AT + 60_000L,
        ).toWearState(NAMES, PUBLISHED_AT)!!

        assertEquals(PUBLISHED_AT, resting.publishedAtElapsedRealtimeMs)
        assertEquals(60_000L, resting.restRemainingMs())
    }

    /**
     * The one that would have caught the uptime bug, from the sending side.
     *
     * `PUBLISHED_AT` is a deliberately large phone uptime. A projection that
     * measured a deadline against anything else — zero, or the wall clock —
     * still satisfies every assertion phrased as "the remainder is 42 seconds",
     * because the subtraction on the other side would cancel the mistake out.
     * These two are what pin the pair to the same origin.
     */
    @Test
    fun `a countdown is measured against the published clock and not zero`() {
        val holding = snapshot(
            target = ExerciseTarget.Duration(sets = 3, durationMs = 60_000L),
            setEndsAtElapsed = PUBLISHED_AT + 42_000L,
        ).toWearState(NAMES, PUBLISHED_AT)!!

        assertEquals(PUBLISHED_AT + 42_000L, holding.setDeadlineElapsedRealtimeMs)
        assertEquals(PUBLISHED_AT, holding.publishedAtElapsedRealtimeMs)
    }

    private companion object {
        /** An arbitrary phone uptime, deliberately large. */
        const val PUBLISHED_AT = 595_515_000L

        val NAMES = mapOf(
            "0025" to "barbell decline wide-grip press",
            "0043" to "dumbbell incline hammer curl",
        )

        fun exercise(id: String, position: Int, target: ExerciseTarget) = SessionExercise(
            id = "e$position",
            exerciseId = ExerciseId(id),
            position = position,
            target = target,
            restMs = 90_000L,
        )

        fun snapshot(
            phase: SessionPhase = SessionPhase.ACTIVE,
            currentExerciseIndex: Int = 0,
            currentSetIndex: Int = 0,
            revision: Long = 0,
            restEndsAtElapsed: Long? = null,
            setEndsAtElapsed: Long? = null,
            restRemainingMs: Long? = null,
            setRemainingMs: Long? = null,
            target: ExerciseTarget = ExerciseTarget.Reps(sets = 4, reps = 12, weightKg = 60.0),
            exercises: List<SessionExercise>? = null,
        ) = SessionSnapshot(
            sessionId = "today",
            templateId = "t1",
            phase = phase,
            exercises = exercises ?: listOf(
                exercise("0025", 0, target),
                exercise("0043", 1, ExerciseTarget.Reps(sets = 3, reps = 10)),
            ),
            currentExerciseIndex = currentExerciseIndex,
            currentSetIndex = currentSetIndex,
            restEndsAtElapsed = restEndsAtElapsed,
            setEndsAtElapsed = setEndsAtElapsed,
            restRemainingMs = restRemainingMs,
            setRemainingMs = setRemainingMs,
            startedAt = 1_767_225_600_000L,
            revision = revision,
        )
    }
}
