package com.repforth.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.repforth.core.database.entity.ExerciseEntity
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
    val steps: List<ExerciseInstructionStepEntity>,

    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val secondaryMuscles: List<ExerciseSecondaryMuscleEntity>,
)

/**
 * A catalog row, projected straight out of SQLite.
 *
 * Deliberately not [ExerciseWithDetails]: the list shows a name and two chips,
 * and fetching relations for 1,324 rows to render that would read 15,420
 * instruction steps nobody is looking at.
 */
data class ExerciseSummaryRow(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "body_part") val bodyPart: String,
    @ColumnInfo(name = "target") val target: String,
    @ColumnInfo(name = "equipment") val equipment: String,
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
     * The catalog list: search and every MVP filter (§3) in one query.
     *
     * A null facet means "no constraint on this one", which keeps a single
     * query for every combination instead of a method per permutation.
     *
     * [ignoreMuscles] exists because SQL has no empty-`IN` — `x IN ()` is a
     * syntax error, so an unset muscle filter has to be expressed as a flag
     * rather than as an empty list.
     *
     * A muscle matches whether it is the target, the muscle group, or one of
     * the secondary muscles: §3 lists all three as filterable, and a user
     * asking for an exercise that hits their lats does not mean "only as the
     * primary target".
     */
    @Query(
        """
        SELECT e.id, e.name, e.body_part, e.target, e.equipment FROM exercise e
        WHERE (:query = '' OR e.name LIKE '%' || :query || '%')
          AND (:bodyPart IS NULL OR e.body_part = :bodyPart)
          AND (:equipment IS NULL OR e.equipment = :equipment)
          AND (
            :ignoreMuscles
            OR e.target IN (:muscles)
            OR e.muscle_group IN (:muscles)
            OR EXISTS (
              SELECT 1 FROM exercise_secondary_muscle m
              WHERE m.exercise_id = e.id AND m.muscle IN (:muscles)
            )
          )
        ORDER BY e.name
        """
    )
    fun observeCatalog(
        query: String,
        bodyPart: String?,
        equipment: String?,
        muscles: List<String>,
        ignoreMuscles: Boolean,
    ): Flow<List<ExerciseSummaryRow>>
}
