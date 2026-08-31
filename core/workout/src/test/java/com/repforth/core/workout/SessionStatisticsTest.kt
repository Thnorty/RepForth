package com.repforth.core.workout

import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers the Progress tab shows.
 *
 * Worth testing carefully because they are the kind of thing nobody checks by
 * hand: a volume total that is quietly wrong looks exactly like one that is
 * right, and a streak is only ever noticed when it breaks.
 */
class SessionStatisticsTest {

    private val zone = ZoneId.of("Europe/Istanbul")

    private fun at(date: String, hour: Int = 10): Long =
        LocalDateTime.parse("${date}T%02d:00:00".format(hour))
            .atZone(zone).toInstant().toEpochMilli()

    private fun session(
        id: String,
        startedAt: Long,
        phase: SessionPhase = SessionPhase.COMPLETED,
        sets: List<SetOutcome> = emptyList(),
        exerciseId: String = "ex-0",
    ) = SessionSnapshot(
        sessionId = id,
        templateId = "plan-1",
        phase = phase,
        exercises = listOf(
            SessionExercise(
                id = "se-$id",
                exerciseId = ExerciseId(exerciseId),
                position = 0,
                target = ExerciseTarget.Reps(3, 10),
                restMs = 60_000,
                sets = sets,
            ),
        ),
        startedAt = startedAt,
        endedAt = startedAt + 30 * 60_000L,
    )

    private fun done(position: Int, reps: Int?, weight: Double?) =
        SetOutcome(position, skipped = false, reps = reps, weightKg = weight, recordedAt = 0)

    private fun skipped(position: Int) =
        SetOutcome(position, skipped = true, recordedAt = 0)

    @Test
    fun `volume is reps times weight, over completed sets only`() {
        val summary = session(
            "s1",
            at("2026-08-24"),
            sets = listOf(done(0, 10, 60.0), done(1, 8, 60.0), skipped(2)),
        ).toSummary()

        assertEquals(10 * 60.0 + 8 * 60.0, summary.volumeKg, 0.001)
        assertEquals(2, summary.setsCompleted)
        assertEquals(1, summary.setsSkipped)
    }

    /**
     * The difference between what was planned and what was done is the whole
     * reason sets are recorded, so volume must follow the performance.
     */
    @Test
    fun `volume follows what was performed, not what was prescribed`() {
        val summary = session("s1", at("2026-08-24"), sets = listOf(done(0, 5, 100.0))).toSummary()

        assertEquals("Target was 3x10; one set of 5 at 100kg was done", 500.0, summary.volumeKg, 0.001)
    }

    @Test
    fun `bodyweight work contributes no volume rather than a made up number`() {
        val summary = session("s1", at("2026-08-24"), sets = listOf(done(0, 20, null))).toSummary()

        assertEquals(0.0, summary.volumeKg, 0.001)
        assertEquals("The set still happened", 1, summary.setsCompleted)
    }

    @Test
    fun `an abandoned workout is summarised, and marked as not completed`() {
        val summary = session(
            "s1",
            at("2026-08-24"),
            phase = SessionPhase.ABANDONED,
            sets = listOf(done(0, 10, 50.0)),
        ).toSummary()

        assertFalse(summary.completed)
        assertEquals("Abandoning does not unmake the sets", 500.0, summary.volumeKg, 0.001)
    }

    @Test
    fun `an empty history has no streak and no volume`() {
        val progress = emptyList<SessionSnapshot>().toProgress(at("2026-08-29"), zone)

        assertEquals(0, progress.workouts)
        assertEquals(0, progress.streakWeeks)
        assertEquals(0.0, progress.totalVolumeKg, 0.001)
    }

    @Test
    fun `this week counts only workouts in the current monday-to-sunday week`() {
        // 2026-08-29 is a Saturday; its week starts Monday 2026-08-24.
        val history = listOf(
            session("s1", at("2026-08-24")),
            session("s2", at("2026-08-29")),
            session("s3", at("2026-08-23")),
        )

        val progress = history.toProgress(at("2026-08-29"), zone)

        assertEquals(3, progress.workouts)
        assertEquals("Sunday the 23rd belongs to the previous week", 2, progress.workoutsThisWeek)
    }

    @Test
    fun `a streak counts consecutive weeks`() {
        val history = listOf(
            session("s1", at("2026-08-24")),
            session("s2", at("2026-08-17")),
            session("s3", at("2026-08-10")),
        )

        assertEquals(3, history.toProgress(at("2026-08-29"), zone).streakWeeks)
    }

    @Test
    fun `a missed week breaks the streak`() {
        val history = listOf(
            session("s1", at("2026-08-24")),
            // nothing in the week of the 17th
            session("s3", at("2026-08-10")),
        )

        assertEquals(1, history.toProgress(at("2026-08-29"), zone).streakWeeks)
    }

    /**
     * A streak that resets at midnight on Monday tells you nothing on Monday
     * morning, which is exactly when someone opens the app to plan their week.
     */
    @Test
    fun `an empty current week is counted from last week rather than resetting`() {
        val history = listOf(
            session("s1", at("2026-08-24")),
            session("s2", at("2026-08-17")),
        )

        // Monday 2026-08-31, a new week with nothing in it yet.
        assertEquals(2, history.toProgress(at("2026-08-31"), zone).streakWeeks)
    }

    @Test
    fun `two empty weeks do break it`() {
        val history = listOf(session("s1", at("2026-08-17")))

        assertEquals(0, history.toProgress(at("2026-08-31"), zone).streakWeeks)
    }

    /**
     * Weeks start on Monday regardless of locale. Deriving that from the phone's
     * language would make a streak change when someone switches to Turkish.
     */
    @Test
    fun `the week boundary does not depend on the locale`() {
        val sunday = session("s1", at("2026-08-23"))
        val monday = session("s2", at("2026-08-24"))

        val progress = listOf(sunday, monday).toProgress(at("2026-08-24"), zone)

        assertEquals(1, progress.workoutsThisWeek)
    }

    @Test
    fun `most performed ranks by how often an exercise was actually done`() {
        val history = listOf(
            session("s1", at("2026-08-24"), sets = listOf(done(0, 10, 50.0)), exerciseId = "bench"),
            session("s2", at("2026-08-25"), sets = listOf(done(0, 10, 50.0)), exerciseId = "bench"),
            session("s3", at("2026-08-26"), sets = listOf(done(0, 10, 50.0)), exerciseId = "squat"),
        )

        assertEquals(
            listOf(ExerciseId("bench"), ExerciseId("squat")),
            history.mostPerformed(limit = 5),
        )
    }

    @Test
    fun `an exercise whose sets were all skipped does not count as performed`() {
        val history = listOf(
            session("s1", at("2026-08-24"), sets = listOf(skipped(0)), exerciseId = "bench"),
            session("s2", at("2026-08-25"), sets = listOf(done(0, 10, 50.0)), exerciseId = "squat"),
        )

        assertEquals(listOf(ExerciseId("squat")), history.mostPerformed(limit = 5))
    }

    @Test
    fun `totals add up across the whole history`() {
        val history = listOf(
            session("s1", at("2026-08-24"), sets = listOf(done(0, 10, 60.0), skipped(1))),
            session("s2", at("2026-08-25"), sets = listOf(done(0, 5, 100.0))),
        )

        val progress = history.toProgress(at("2026-08-29"), zone)

        assertEquals(1100.0, progress.totalVolumeKg, 0.001)
        assertEquals("Skipped sets are not completed sets", 2, progress.totalSets)
        assertTrue(progress.workouts == 2)
    }

    @Test
    fun `duration is the span from start to end`() {
        val started = at("2026-08-24", hour = 9)
        val summary = session("s1", started).toSummary()

        assertEquals(30 * 60_000L, summary.durationMs)
    }

    @Test
    fun `multiple sessions on the same day count as one day trained this week`() {
        val history = listOf(
            session("s1", at("2026-08-24", hour = 8)),
            session("s2", at("2026-08-24", hour = 12)),
            session("s3", at("2026-08-24", hour = 18)),
            session("s4", at("2026-08-25", hour = 10)),
        )

        val progress = history.toProgress(at("2026-08-26"), zone)

        assertEquals(4, progress.workoutsThisWeek)
        assertEquals("Aug 24 (3 sessions) and Aug 25 (1 session) are 2 distinct days", 2, progress.daysThisWeek)
    }
}
