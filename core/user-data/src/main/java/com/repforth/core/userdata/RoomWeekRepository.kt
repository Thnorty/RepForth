package com.repforth.core.userdata

import com.repforth.core.common.time.TimeSource
import com.repforth.core.database.dao.TemplateWithExercises
import com.repforth.core.database.dao.WeekDao
import com.repforth.core.database.dao.WeekWithDays
import com.repforth.core.database.entity.TemplateExerciseEntity
import com.repforth.core.database.entity.TrainingWeekEntity
import com.repforth.core.database.entity.WorkoutTemplateEntity
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.WeekDay
import com.repforth.core.model.WorkoutTemplate
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomWeekRepository @Inject constructor(
    private val dao: WeekDao,
    private val time: TimeSource,
) : WeekRepository {

    override fun observeAll(): Flow<List<TrainingWeek>> =
        dao.observeAll().map { rows -> rows.map(WeekWithDays::toDomain) }

    override fun observeActive(): Flow<TrainingWeek?> =
        dao.observeActive().map { it?.toDomain() }

    override suspend fun find(id: String): TrainingWeek? = dao.findById(id)?.toDomain()

    override suspend fun save(week: TrainingWeek) {
        val now = time.now()
        val existing = dao.findById(week.id)
        val createdAt = existing?.week?.createdAt ?: now

        val weekEntity = TrainingWeekEntity(
            id = week.id,
            name = week.name,
            notes = week.notes,
            source = week.source.name,
            active = week.active,
            createdAt = createdAt,
            updatedAt = now,
        )

        val templateEntities = mutableListOf<WorkoutTemplateEntity>()
        val exerciseEntities = mutableListOf<TemplateExerciseEntity>()

        week.days.forEach { day ->
            val template = day.workout
            val tmplCreatedAt = existing?.days?.firstOrNull { it.template.id == template.id }?.template?.createdAt ?: now

            templateEntities += WorkoutTemplateEntity(
                id = template.id,
                name = template.name,
                notes = template.notes,
                source = template.source.name,
                weekId = week.id,
                weekPosition = day.position,
                dayOfWeek = day.dayOfWeek?.value,
                createdAt = tmplCreatedAt,
                updatedAt = now,
            )

            template.exercises.forEach { planned ->
                val reps = planned.target as? ExerciseTarget.Reps
                val duration = planned.target as? ExerciseTarget.Duration
                exerciseEntities += TemplateExerciseEntity(
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
            }
        }

        dao.replaceWeek(
            week = weekEntity,
            templates = templateEntities,
            exercises = exerciseEntities,
        )
    }

    override suspend fun setActive(id: String) = dao.setActive(id)

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()
}

private fun WeekWithDays.toDomain(): TrainingWeek = TrainingWeek(
    id = week.id,
    name = week.name,
    notes = week.notes,
    source = PlanSource.entries.firstOrNull { it.name == week.source } ?: PlanSource.MANUAL,
    active = week.active,
    days = days.sortedBy { it.template.weekPosition ?: 0 }.map { it.toWeekDay() },
)

private fun TemplateWithExercises.toWeekDay(): WeekDay = WeekDay(
    position = template.weekPosition ?: 0,
    title = template.name,
    dayOfWeek = template.dayOfWeek?.let { DayOfWeek.of(it) },
    workout = toWorkoutTemplate(),
)

private fun TemplateWithExercises.toWorkoutTemplate(): WorkoutTemplate = WorkoutTemplate(
    id = template.id,
    name = template.name,
    notes = template.notes,
    source = PlanSource.entries.firstOrNull { it.name == template.source } ?: PlanSource.MANUAL,
    exercises = exercises.sortedBy { it.position }.map { it.toPlannedExercise() },
)

private fun TemplateExerciseEntity.toPlannedExercise(): PlannedExercise = PlannedExercise(
    id = id,
    exerciseId = ExerciseId(exerciseId),
    position = position,
    target = exerciseTargetOf(targetSets, targetReps, targetDurationMs, targetWeightKg),
    restMs = restMs,
)
