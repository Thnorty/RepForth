package com.repforth.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.repforth.core.database.entity.TemplateExerciseEntity
import com.repforth.core.database.entity.TrainingWeekEntity
import com.repforth.core.database.entity.WorkoutTemplateEntity
import kotlinx.coroutines.flow.Flow

data class WeekWithDays(
    @Embedded val week: TrainingWeekEntity,

    @Relation(
        entity = WorkoutTemplateEntity::class,
        parentColumn = "id",
        entityColumn = "week_id",
    )
    val days: List<TemplateWithExercises>,
)

@Dao
interface WeekDao {

    @Transaction
    @Query("SELECT * FROM training_week ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<WeekWithDays>>

    @Transaction
    @Query("SELECT * FROM training_week WHERE active = 1 LIMIT 1")
    fun observeActive(): Flow<WeekWithDays?>

    @Transaction
    @Query("SELECT * FROM training_week WHERE id = :id")
    suspend fun findById(id: String): WeekWithDays?

    /**
     * Saves a week and all its day templates and exercises in one transaction (§3).
     *
     * Following the precedent of [TemplateDao.replaceTemplate], templates and
     * exercises are replaced wholesale so a partial failure never leaves orphan
     * or non-contiguous records.
     */
    @Transaction
    suspend fun replaceWeek(
        week: TrainingWeekEntity,
        templates: List<WorkoutTemplateEntity>,
        exercises: List<TemplateExerciseEntity>,
    ) {
        if (week.active) {
            clearActive()
        }
        upsertWeek(week)
        deleteTemplatesForWeek(week.id)
        insertTemplates(templates)
        insertExercises(exercises)
    }

    /**
     * Sets exactly one week as active in one transaction (§3.1).
     */
    @Transaction
    suspend fun setActive(id: String) {
        clearActive()
        setWeekActive(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeek(week: TrainingWeekEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(rows: List<WorkoutTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(rows: List<TemplateExerciseEntity>)

    @Query("DELETE FROM workout_template WHERE week_id = :weekId")
    suspend fun deleteTemplatesForWeek(weekId: String)

    @Query("UPDATE training_week SET active = 0")
    suspend fun clearActive()

    @Query("UPDATE training_week SET active = 1 WHERE id = :id")
    suspend fun setWeekActive(id: String)

    /**
     * Deletes the week. Member templates cascade-delete through the schema.
     */
    @Query("DELETE FROM training_week WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM training_week")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM training_week")
    suspend fun count(): Int
}
