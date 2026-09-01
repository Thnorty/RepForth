package com.repforth.core.transfer

import kotlinx.serialization.Serializable

/**
 * The export file format (§7).
 *
 * Its own types rather than serialising the domain model directly. The domain is
 * free to be renamed, split, or given an invariant tomorrow; a file someone
 * exported last year is not. Keeping them apart means a refactor cannot silently
 * change what an old file means, and the mapping between them is the one place
 * that has to know about both.
 *
 * [format] and [version] are checked before anything is read. A file that does
 * not identify itself is refused rather than guessed at — §7 requires import to
 * validate the schema first, and the failure mode of guessing is overwriting a
 * user's only copy of their history with something misparsed.
 */
@Serializable
data class ExportDocument(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** Wall clock, for the reader's benefit. Never used to order anything. */
    val exportedAt: Long,
    val profile: ProfileDto? = null,
    val templates: List<TemplateDto> = emptyList(),
    /**
     * Weekly plans and the workouts inside them.
     *
     * Separate from [templates] rather than folded into it, because
     * `TemplateRepository.observeAll()` deliberately returns only standalone
     * workouts — a week's days are reached through the week. If a week's days
     * were also listed in [templates] an import would create them twice: once
     * loose, once inside the week.
     */
    val weeks: List<WeekDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
) {
    companion object {
        const val FORMAT = "repforth.export"

        /**
         * Bumped when the shape changes in a way an older reader cannot handle.
         *
         * Import accepts this version and below. Accepting a *newer* file would
         * mean reading fields whose meaning had not been decided when this code
         * was written.
         *
         * Version 2 added [weeks]. A version 1 file still imports and simply
         * has none, which is correct: weekly plans did not exist when it was
         * written.
         */
        const val VERSION = 2
    }
}

@Serializable
data class ProfileDto(
    val id: String,
    val goal: String,
    val experience: String,
    val trainingDaysPerWeek: Int,
    val sessionLengthMs: Long,
    val equipment: List<String> = emptyList(),
    val preferredMuscles: List<String> = emptyList(),
    val exclusions: List<ExclusionDto> = emptyList(),
)

@Serializable
data class ExclusionDto(val kind: String, val value: String)

@Serializable
data class TemplateDto(
    val id: String,
    val name: String,
    val notes: String? = null,
    val source: String,
    val exercises: List<PlannedExerciseDto> = emptyList(),
)

/**
 * A weekly plan, with its days inline.
 *
 * The days carry their whole workout rather than an id pointing at one, so a
 * week is self-contained in the file. An id reference would let an export be
 * written whose week names a workout the same file does not contain, and the
 * import would then have to decide what to do about it.
 */
@Serializable
data class WeekDto(
    val id: String,
    val name: String,
    val notes: String? = null,
    val source: String,
    val active: Boolean = false,
    val days: List<WeekDayDto> = emptyList(),
)

@Serializable
data class WeekDayDto(
    val position: Int,
    val title: String,
    /** ISO day number, Monday = 1. Null when the day is not pinned to a weekday. */
    val dayOfWeek: Int? = null,
    val workout: TemplateDto,
)

@Serializable
data class PlannedExerciseDto(
    val id: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val reps: Int? = null,
    val durationMs: Long? = null,
    val weightKg: Double? = null,
    val restMs: Long,
)

@Serializable
data class SessionDto(
    val id: String,
    val templateId: String? = null,
    val phase: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val exercises: List<SessionExerciseDto> = emptyList(),
)

@Serializable
data class SessionExerciseDto(
    val id: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val reps: Int? = null,
    val durationMs: Long? = null,
    val weightKg: Double? = null,
    val restMs: Long,
    val outcomes: List<SetOutcomeDto> = emptyList(),
)

@Serializable
data class SetOutcomeDto(
    val position: Int,
    val skipped: Boolean,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationMs: Long? = null,
    val rpe: Int? = null,
    val recordedAt: Long,
)

/**
 * What importing this file would do, shown before it is done (§7).
 *
 * Counted rather than described, because the only question that matters before
 * overwriting the only copy of something is how much of it there is.
 */
data class ImportPreview(
    val hasProfile: Boolean,
    val replacesExistingProfile: Boolean,
    val newTemplates: Int,
    val replacedTemplates: Int,
    val sessions: Int,
    val exportedAt: Long,
    val newWeeks: Int = 0,
    val replacedWeeks: Int = 0,
) {
    val isEmpty: Boolean
        get() = !hasProfile && newTemplates == 0 && replacedTemplates == 0 &&
            newWeeks == 0 && replacedWeeks == 0 && sessions == 0
}

/** Why a file could not be read. Each one is something to tell the user. */
sealed interface ImportFailure {
    /** Not JSON, or not JSON shaped like anything this reads. */
    data class Unreadable(val detail: String) : ImportFailure

    /** Valid JSON, but not one of ours. */
    data class WrongFormat(val found: String) : ImportFailure

    /** Ours, but from a later version of the app. */
    data class TooNew(val found: Int, val supported: Int) : ImportFailure

    /**
     * Ours and readable, but the contents break a rule the domain enforces —
     * a plan with no name, a target with zero sets, positions with a gap.
     */
    data class Invalid(val detail: String) : ImportFailure
}
