package com.repforth.core.wearsync

import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.restRemainingMs
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

    @Test
    fun `the rest deadline crosses over untouched`() {
        val resting = snapshot(
            phase = SessionPhase.RESTING,
            restEndsAtElapsed = 123_456L,
        )
        assertEquals(123_456L, resting.toWearState(NAMES, PUBLISHED_AT)!!.deadlineElapsedRealtimeMs)
    }

    @Test
    fun `the next exercise is named while there is one`() {
        assertEquals(
            "dumbbell incline hammer curl",
            snapshot().toWearState(NAMES, PUBLISHED_AT)!!.nextExerciseName,
        )
    }

    @Test
    fun `the last exercise has nothing after it`() {
        val onTheLast = snapshot(currentExerciseIndex = 1)
        assertNull(onTheLast.toWearState(NAMES, PUBLISHED_AT)!!.nextExerciseName)
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
            startedAt = 1_767_225_600_000L,
            revision = revision,
        )
    }
}
