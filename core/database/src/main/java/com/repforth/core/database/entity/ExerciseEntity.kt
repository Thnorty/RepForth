package com.repforth.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The imported exercise catalog (§7).
 *
 * Read-only at runtime: rows are produced by the import task and replaced
 * wholesale when the pinned dataset commit changes. That is why these tables
 * carry no `createdAt`/`updatedAt` — those are required for *mutable* tables,
 * and nothing the user does edits the catalog.
 *
 * Media deliberately does not appear here. URLs, hashes and sizes live in
 * `media-manifest.json` so that the licensing boundary (§6) is a real seam: a
 * `placeholder` build resolves media to [MediaRef.Unavailable] without the
 * catalog knowing anything about it. Attribution goes there too: the dataset
 * uses one identical string for every record, so a column would have stored the
 * same sentence 1,324 times, and it describes the media rather than the
 * exercise.
 *
 * Categorical columns hold the upstream slug, not an enum name. That is the
 * original value (§6: preserve it), and it means a renamed Kotlin constant
 * cannot invalidate a prepackaged database.
 */
@Entity(tableName = "exercise")
data class ExerciseEntity(
    /** The upstream dataset ID, preserved verbatim. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "body_part", index = true)
    val bodyPart: String,

    @ColumnInfo(name = "target", index = true)
    val target: String,

    @ColumnInfo(name = "muscle_group", index = true)
    val muscleGroup: String,

    @ColumnInfo(name = "equipment", index = true)
    val equipment: String,
)

/**
 * A junction table rather than a delimited column, so "what else does this hit?"
 * and "what hits this?" are both indexed queries. Filtering the catalog by
 * secondary muscle is a listed MVP filter (§3), and a comma-joined string cannot
 * serve it.
 */
@Entity(
    tableName = "exercise_secondary_muscle",
    primaryKeys = ["exercise_id", "muscle"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("muscle")],
)
data class ExerciseSecondaryMuscleEntity(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    @ColumnInfo(name = "muscle")
    val muscle: String,
)
