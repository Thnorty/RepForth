package com.repforth.core.workout

import com.repforth.core.model.WorkoutTemplate

/**
 * Which saved plan to offer next (§12: Today shows the current or recommended
 * workout).
 *
 * The one you have left longest. Not the newest plan, and not the one you did
 * most recently — a plan you have never performed outranks everything, because
 * a plan someone built and never ran is the one most likely to be what they
 * meant to do next.
 *
 * Deliberately not a training-science decision. It does not know about push/pull
 * splits or muscle recovery, and pretending otherwise would be inventing
 * programming advice the rules engine has not been asked for. It answers a
 * simpler question honestly: of the plans you keep, which is the stalest?
 *
 * Pure, so the ordering is testable without a database and cannot depend on the
 * order rows came back in.
 */
fun recommendNext(
    plans: List<WorkoutTemplate>,
    history: List<SessionSnapshot>,
): WorkoutTemplate? {
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
