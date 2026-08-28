package com.repforth.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.repforth.core.database.entity.ExerciseEntity
import com.repforth.core.database.entity.ExerciseInstructionEntity
import com.repforth.core.database.entity.ExerciseInstructionStepEntity
import com.repforth.core.database.entity.ExerciseSecondaryMuscleEntity
import kotlinx.coroutines.flow.Flow

/**
 * An exercise with everything needed to render its detail screen, fetched in one
 * transaction so a row cannot be read half-updated.
 *
 * Both languages are loaded together, not just the current locale: §13 requires
 * switching language without a reload, and the payload is a few hundred bytes.
 */
data class ExerciseWithDetails(
    @Embedded val exercise: ExerciseEntity,

    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val instructions: List<ExerciseInstructionEntity>,

    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val steps: List<ExerciseInstructionStepEntity>,

    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val secondaryMuscles: List<ExerciseSecondaryMuscleEntity>,
)

@Dao
interface ExerciseDao {

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Transaction
    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun findById(id: String): ExerciseWithDetails?

    /**
     * Catalog browsing. Ordered by name so paging is stable; a Flow so the list
     * survives the catalog being replaced by a dataset update underneath it.
     */
    @Transaction
    @Query("SELECT * FROM exercise ORDER BY name LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<ExerciseWithDetails>>

    /**
     * Filtering. A null argument means "no constraint on this facet", which
     * keeps one query for every combination of the MVP filters (§3) instead of
     * a method per permutation.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM exercise
        WHERE (:bodyPart IS NULL OR body_part = :bodyPart)
          AND (:equipment IS NULL OR equipment = :equipment)
          AND (:target IS NULL OR target = :target)
        ORDER BY name
        """
    )
    fun observeFiltered(
        bodyPart: String?,
        equipment: String?,
        target: String?,
    ): Flow<List<ExerciseWithDetails>>
}
