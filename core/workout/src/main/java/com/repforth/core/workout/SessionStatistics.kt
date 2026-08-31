package com.repforth.core.workout

import com.repforth.core.model.ExerciseId
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * What a finished workout amounts to.
 *
 * Derived rather than stored, so it cannot disagree with the sets it came from.
 * Recomputing is cheap — a workout has tens of sets, not thousands — and a
 * stored total is a second source of truth that drifts the first time a set is
 * edited.
 */
data class WorkoutSummary(
    val sessionId: String,
    val templateId: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val completed: Boolean,
    val setsCompleted: Int,
    val setsSkipped: Int,
    val exerciseCount: Int,
    /** Kilograms lifted: reps times weight, summed. Zero for bodyweight work. */
    val volumeKg: Double,
) {
    val durationMs: Long? get() = endedAt?.let { it - startedAt }
}

/**
 * The Progress tab's headline figures (§12: history, streaks, volume).
 */
data class ProgressSummary(
    val workouts: Int = 0,
    val workoutsThisWeek: Int = 0,
    /** Distinct calendar days with at least one workout in the current week. */
    val daysThisWeek: Int = 0,
    /** Consecutive weeks, ending with this one, containing at least one workout. */
    val streakWeeks: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val totalSets: Int = 0,
    /** Most-trained muscles recently, most first. Empty until the catalog is joined. */
    val topMuscles: List<String> = emptyList(),
)

/**
 * Volume counts what was lifted, not what was planned.
 *
 * A skipped set contributes nothing, and a set completed at a different weight
 * than prescribed counts at the weight performed — the difference between plan
 * and performance is the entire reason sets are recorded rather than assumed.
 * Bodyweight work has no weight and so no volume, which understates it; §12 asks
 * for volume rather than an effort score, and inventing a number for bodyweight
 * would be inventing data.
 */
fun SessionSnapshot.toSummary(): WorkoutSummary {
    val outcomes = exercises.flatMap { it.sets }
    return WorkoutSummary(
        sessionId = sessionId,
        templateId = templateId,
        startedAt = startedAt,
        endedAt = endedAt,
        completed = phase == SessionPhase.COMPLETED,
        setsCompleted = outcomes.count { !it.skipped },
        setsSkipped = outcomes.count { it.skipped },
        exerciseCount = exercises.size,
        volumeKg = outcomes
            .filterNot { it.skipped }
            .sumOf { (it.reps ?: 0) * (it.weightKg ?: 0.0) },
    )
}

/**
 * Rolls a history up into the figures the Progress tab shows.
 *
 * [zone] is a parameter rather than `ZoneId.systemDefault()` because "this week"
 * is a question about where the user is, and because a streak that changes
 * depending on which machine computed it is not testable.
 */
fun List<SessionSnapshot>.toProgress(
    nowMillis: Long,
    zone: ZoneId,
): ProgressSummary {
    val summaries = map { it.toSummary() }
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val thisWeekSummaries = summaries.filter { it.startedAt.weekOf(zone) == today.weekStart() }
    val daysThisWeek = thisWeekSummaries
        .map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .distinct()
        .size

    return ProgressSummary(
        workouts = summaries.size,
        workoutsThisWeek = thisWeekSummaries.size,
        daysThisWeek = daysThisWeek,
        streakWeeks = summaries.streakWeeks(today, zone),
        totalVolumeKg = summaries.sumOf { it.volumeKg },
        totalSets = summaries.sumOf { it.setsCompleted },
    )
}

/**
 * Consecutive weeks with at least one workout, counted back from this one.
 *
 * Weeks rather than days on purpose: §3 asks how many days a week someone
 * trains, so a rest day is part of the plan and a day-based streak would punish
 * following it. An empty current week does not break the streak either — it is
 * counted from last week in that case, because a streak that resets on Monday
 * morning tells you nothing on Monday morning.
 */
private fun List<WorkoutSummary>.streakWeeks(today: LocalDate, zone: ZoneId): Int {
    if (isEmpty()) return 0

    val weeks = mapTo(mutableSetOf()) { it.startedAt.weekOf(zone) }
    var cursor = today.weekStart()
    if (cursor !in weeks) cursor = cursor.minusWeeks(1)

    var streak = 0
    while (cursor in weeks) {
        streak++
        cursor = cursor.minusWeeks(1)
    }
    return streak
}

/** The Monday of the week this instant falls in, in [zone]. */
private fun Long.weekOf(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate().weekStart()

/**
 * ISO weeks: Monday starts the week, everywhere.
 *
 * [WeekFields] would follow the locale, which would make a streak change when
 * the phone's language changes. A workout history is not a calendar.
 */
private fun LocalDate.weekStart(): LocalDate = with(DayOfWeek.MONDAY)

/** Exercise ids in a history, most frequently performed first. */
fun List<SessionSnapshot>.mostPerformed(limit: Int): List<ExerciseId> =
    flatMap { session -> session.exercises.filter { it.sets.any { set -> !set.skipped } } }
        .groupingBy { it.exerciseId }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<ExerciseId, Int>> { it.value }
            .thenBy { it.key.value })
        .take(limit)
        .map { it.key }
