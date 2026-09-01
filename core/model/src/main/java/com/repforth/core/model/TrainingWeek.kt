package com.repforth.core.model

import java.time.DayOfWeek

/**
 * A week of training (§1, §3).
 *
 * A week is a container of workouts, not a new kind of workout. Each day inside
 * a week is backed by a [WorkoutTemplate], and a week never directly holds
 * exercises.
 */
data class TrainingWeek(
    val id: String,
    val name: String,
    val notes: String? = null,
    val source: PlanSource,
    val active: Boolean,
    val days: List<WeekDay>,
) {
    init {
        require(name.isNotBlank()) { "A week needs a name" }
        require(days.map { it.position } == days.indices.toList()) {
            "Day positions must be contiguous from zero, in order"
        }
        require(days.mapNotNull { it.dayOfWeek }.let { it.size == it.toSet().size }) {
            "Assigned days of week must be unique across the week"
        }
    }

    /** Total estimated duration across all days in the week. */
    val estimatedDurationMs: Long
        get() = days.sumOf { it.workout.estimatedDurationMs }
}

/** One day within a [TrainingWeek], backed by a [WorkoutTemplate]. */
data class WeekDay(
    val position: Int,
    val title: String,
    val dayOfWeek: DayOfWeek? = null,
    val workout: WorkoutTemplate,
) {
    init {
        require(position >= 0) { "Day position must not be negative" }
        require(title.isNotBlank()) { "Day title must not be blank" }
    }
}
