package com.repforth.core.workout

import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.WorkoutTemplate

/**
 * Which saved plan to offer next (§12: Today shows the current or recommended
 * workout).
 *
 * When an active [activeWeek] is set, the sequence of days inside the week
 * takes priority: the next unperformed day in the rotation (or the least
 * recently performed day of the week) is recommended.
 *
 * Otherwise, falls back to the stalest standalone plan from [plans].
 */
fun recommendNext(
    plans: List<WorkoutTemplate>,
    history: List<SessionSnapshot>,
    activeWeek: TrainingWeek? = null,
): WorkoutTemplate? {
    if (activeWeek != null && activeWeek.days.isNotEmpty()) {
        val weekPlans = activeWeek.days.map { it.workout }
        val lastPerformed = history
            .filter { it.templateId != null }
            .groupBy { it.templateId }
            .mapValues { (_, sessions) -> sessions.maxOf { it.startedAt } }

        return weekPlans.minWithOrNull(
            compareBy<WorkoutTemplate> { lastPerformed[it.id] ?: Long.MIN_VALUE }
                .thenBy { template -> activeWeek.days.indexOfFirst { it.workout.id == template.id } },
        )
    }

    if (plans.isEmpty()) return null

    val lastPerformed = history
        .filter { it.templateId != null }
        .groupBy { it.templateId }
        .mapValues { (_, sessions) -> sessions.maxOf { it.startedAt } }

    return plans.minWithOrNull(
        // Never performed sorts first: Long.MIN_VALUE is older than any
        // timestamp. Ties break on name so the same library always suggests the
        // same plan rather than whichever the query happened to return first.
        compareBy<WorkoutTemplate> { lastPerformed[it.id] ?: Long.MIN_VALUE }
            .thenBy { it.name },
    )
}
