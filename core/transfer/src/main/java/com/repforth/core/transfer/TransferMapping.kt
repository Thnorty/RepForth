package com.repforth.core.transfer

import com.repforth.core.model.Equipment
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.workout.SessionExercise
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import com.repforth.core.workout.SetOutcome

/*
 * Domain to file, and back.
 *
 * Enums cross as their names rather than their ordinals: an ordinal is a
 * position in a list that someone will reorder, and reordering an enum would
 * silently turn every exported "strength" into "hypertrophy". A name that no
 * longer exists is a readable failure; a shifted ordinal is a wrong answer.
 */

internal fun UserProfile.toDto() = ProfileDto(
    id = id,
    goal = goal.name,
    experience = experience.name,
    trainingDaysPerWeek = trainingDaysPerWeek,
    sessionLengthMs = sessionLengthMs,
    equipment = availableEquipment.map { it.name }.sorted(),
    preferredMuscles = preferredMuscles.map { it.name }.sorted(),
    exclusions = exclusions.map { ExclusionDto(it.kind.name, it.value) }
        .sortedBy { it.kind + it.value },
)

internal fun ProfileDto.toDomain() = UserProfile(
    id = id,
    goal = enumOf<TrainingGoal>(goal, "goal"),
    experience = enumOf<ExperienceLevel>(experience, "experience"),
    trainingDaysPerWeek = trainingDaysPerWeek,
    sessionLengthMs = sessionLengthMs,
    availableEquipment = equipment.mapTo(mutableSetOf()) { enumOf<Equipment>(it, "equipment") },
    preferredMuscles = preferredMuscles.mapTo(mutableSetOf()) { enumOf<Muscle>(it, "muscle") },
    exclusions = exclusions.mapTo(mutableSetOf()) {
        MovementExclusion(enumOf<ExclusionKind>(it.kind, "exclusion kind"), it.value)
    },
)

internal fun WorkoutTemplate.toDto() = TemplateDto(
    id = id,
    name = name,
    notes = notes,
    source = source.name,
    exercises = exercises.map { planned ->
        val reps = planned.target as? ExerciseTarget.Reps
        val duration = planned.target as? ExerciseTarget.Duration
        PlannedExerciseDto(
            id = planned.id,
            exerciseId = planned.exerciseId.value,
            position = planned.position,
            sets = planned.target.sets,
            reps = reps?.reps,
            durationMs = duration?.durationMs,
            weightKg = planned.target.weightKg,
            restMs = planned.restMs,
        )
    },
)

internal fun TemplateDto.toDomain() = WorkoutTemplate(
    id = id,
    name = name,
    notes = notes,
    source = enumOf<PlanSource>(source, "plan source"),
    exercises = exercises
        // The domain requires positions contiguous from zero in order, and a
        // file can say otherwise. Sorting fixes the order; the constructor
        // still rejects a genuine gap, which is the honest outcome.
        .sortedBy { it.position }
        .map { dto ->
            PlannedExercise(
                id = dto.id,
                exerciseId = ExerciseId(dto.exerciseId),
                position = dto.position,
                target = dto.target(),
                restMs = dto.restMs,
            )
        },
)

internal fun SessionSnapshot.toDto() = SessionDto(
    id = sessionId,
    templateId = templateId,
    phase = phase.name,
    startedAt = startedAt,
    endedAt = endedAt,
    exercises = exercises.map { exercise ->
        val reps = exercise.target as? ExerciseTarget.Reps
        val duration = exercise.target as? ExerciseTarget.Duration
        SessionExerciseDto(
            id = exercise.id,
            exerciseId = exercise.exerciseId.value,
            position = exercise.position,
            sets = exercise.target.sets,
            reps = reps?.reps,
            durationMs = duration?.durationMs,
            weightKg = exercise.target.weightKg,
            restMs = exercise.restMs,
            outcomes = exercise.sets.map { outcome ->
                SetOutcomeDto(
                    position = outcome.position,
                    skipped = outcome.skipped,
                    reps = outcome.reps,
                    weightKg = outcome.weightKg,
                    durationMs = outcome.durationMs,
                    rpe = outcome.rpe,
                    recordedAt = outcome.recordedAt,
                )
            },
        )
    },
)

internal fun SessionDto.toDomain() = SessionSnapshot(
    sessionId = id,
    templateId = templateId,
    phase = enumOf<SessionPhase>(phase, "session phase"),
    exercises = exercises.sortedBy { it.position }.map { dto ->
        SessionExercise(
            id = dto.id,
            exerciseId = ExerciseId(dto.exerciseId),
            position = dto.position,
            target = dto.target(),
            restMs = dto.restMs,
            sets = dto.outcomes.sortedBy { it.position }.map { outcome ->
                SetOutcome(
                    position = outcome.position,
                    skipped = outcome.skipped,
                    reps = outcome.reps,
                    weightKg = outcome.weightKg,
                    durationMs = outcome.durationMs,
                    rpe = outcome.rpe,
                    recordedAt = outcome.recordedAt,
                )
            },
        )
    },
    startedAt = startedAt,
    endedAt = endedAt,
)

/**
 * Rebuilds a target from the file's flat columns.
 *
 * Same rule the two database tables use: reps wins when a row somehow has both,
 * and neither present reads as a single rep. Stated here rather than shared with
 * `exerciseTargetOf` because that one is internal to `core:user-data`, and
 * reaching across a module boundary for four lines would couple the file format
 * to the storage layer.
 */
private fun PlannedExerciseDto.target(): ExerciseTarget = when {
    reps != null -> ExerciseTarget.Reps(sets, reps, weightKg)
    durationMs != null -> ExerciseTarget.Duration(sets, durationMs, weightKg)
    else -> ExerciseTarget.Reps(sets, reps = 1, weightKg = weightKg)
}

private fun SessionExerciseDto.target(): ExerciseTarget = when {
    reps != null -> ExerciseTarget.Reps(sets, reps, weightKg)
    durationMs != null -> ExerciseTarget.Duration(sets, durationMs, weightKg)
    else -> ExerciseTarget.Reps(sets, reps = 1, weightKg = weightKg)
}

/**
 * An enum constant by name, with a message naming what failed.
 *
 * `valueOf` throws `IllegalArgumentException` with only the constant name in it,
 * which tells a user nothing about which field of their file is wrong.
 */
private inline fun <reified T : Enum<T>> enumOf(name: String, field: String): T =
    enumValues<T>().firstOrNull { it.name == name }
        ?: throw IllegalArgumentException("Unknown $field: \"$name\"")
