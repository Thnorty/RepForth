package com.repforth.core.userdata

import com.repforth.core.common.time.TimeSource
import com.repforth.core.database.dao.TemplateDao
import com.repforth.core.database.dao.TemplateWithExercises
import com.repforth.core.database.entity.TemplateExerciseEntity
import com.repforth.core.database.entity.WorkoutTemplateEntity
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomTemplateRepository @Inject constructor(
    private val dao: TemplateDao,
    private val time: TimeSource,
) : TemplateRepository {

    override fun observeAll(): Flow<List<WorkoutTemplate>> =
        dao.observeAll().map { rows -> rows.map(TemplateWithExercises::toDomain) }

    override suspend fun find(id: String): WorkoutTemplate? = dao.findById(id)?.toDomain()

    override suspend fun save(template: WorkoutTemplate) {
        val now = time.now()
        val createdAt = dao.findById(template.id)?.template?.createdAt ?: now

        dao.replaceTemplate(
            template = WorkoutTemplateEntity(
                id = template.id,
                name = template.name,
                notes = template.notes,
                source = template.source.name,
                createdAt = createdAt,
                updatedAt = now,
            ),
            exercises = template.exercises.map { planned ->
                val reps = planned.target as? ExerciseTarget.Reps
                val duration = planned.target as? ExerciseTarget.Duration
                TemplateExerciseEntity(
                    id = planned.id,
                    templateId = template.id,
                    exerciseId = planned.exerciseId.value,
                    position = planned.position,
                    targetSets = planned.target.sets,
                    targetReps = reps?.reps,
                    targetDurationMs = duration?.durationMs,
                    targetWeightKg = planned.target.weightKg,
                    restMs = planned.restMs,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()
}

private fun TemplateWithExercises.toDomain() = WorkoutTemplate(
    id = template.id,
    name = template.name,
    notes = template.notes,
    source = PlanSource.entries.firstOrNull { it.name == template.source } ?: PlanSource.MANUAL,
    // Sorted here rather than trusted: the domain type requires contiguous
    // positions in order and will refuse to construct otherwise, and SQL makes
    // no ordering promise without an ORDER BY on the relation.
    exercises = exercises.sortedBy { it.position }.map { it.toDomain() },
)

private fun TemplateExerciseEntity.toDomain() = PlannedExercise(
    id = id,
    exerciseId = ExerciseId(exerciseId),
    position = position,
    target = toTarget(),
    restMs = restMs,
)

/**
 * Reps wins when a row somehow has both.
 *
 * The schema allows it — two nullable columns cannot express "exactly one" — and
 * the domain type does not. Preferring reps is arbitrary but total; the
 * alternative is throwing, which would make one malformed row hide a whole plan.
 */
private fun TemplateExerciseEntity.toTarget(): ExerciseTarget = when {
    targetReps != null -> ExerciseTarget.Reps(targetSets, targetReps!!, targetWeightKg)
    targetDurationMs != null -> ExerciseTarget.Duration(targetSets, targetDurationMs!!, targetWeightKg)
    // Neither set: treat as a single-rep target rather than losing the exercise.
    else -> ExerciseTarget.Reps(targetSets, reps = 1, weightKg = targetWeightKg)
}
