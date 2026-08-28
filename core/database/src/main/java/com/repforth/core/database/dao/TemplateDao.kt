package com.repforth.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.repforth.core.database.entity.TemplateExerciseEntity
import com.repforth.core.database.entity.WorkoutTemplateEntity
import kotlinx.coroutines.flow.Flow

data class TemplateWithExercises(
    @Embedded val template: WorkoutTemplateEntity,

    @Relation(parentColumn = "id", entityColumn = "template_id")
    val exercises: List<TemplateExerciseEntity>,
)

@Dao
interface TemplateDao {

    @Transaction
    @Query("SELECT * FROM workout_template ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<TemplateWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_template WHERE id = :id")
    suspend fun findById(id: String): TemplateWithExercises?

    /**
     * Saves a plan and its exercises together.
     *
     * Children are deleted and re-inserted rather than diffed. Reordering a plan
     * changes almost every row's position anyway, and a partially-applied
     * reorder is a plan with two exercises claiming position 3 — which the
     * domain type refuses to construct, so it would surface as a crash on read
     * rather than as the mistake it is.
     */
    @Transaction
    suspend fun replaceTemplate(
        template: WorkoutTemplateEntity,
        exercises: List<TemplateExerciseEntity>,
    ) {
        upsertTemplate(template)
        deleteExercisesFor(template.id)
        insertExercises(exercises)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: WorkoutTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(rows: List<TemplateExerciseEntity>)

    @Query("DELETE FROM template_exercise WHERE template_id = :templateId")
    suspend fun deleteExercisesFor(templateId: String)

    /**
     * Deletes the plan only. Sessions performed from it survive: `workout_session`
     * has no foreign key here, because the record of having trained something
     * outlives the intention to train it.
     */
    @Query("DELETE FROM workout_template WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM workout_template")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM workout_template")
    suspend fun count(): Int
}
