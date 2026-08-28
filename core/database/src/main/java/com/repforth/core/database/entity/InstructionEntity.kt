package com.repforth.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One language's instructions for one exercise.
 *
 * Language is a column, not a suffixed pair of columns (`summary_en`,
 * `summary_tr`), because §13 treats the two languages as equals. A schema that
 * hard-codes them makes English structurally privileged and makes a third
 * language a migration instead of a row.
 */
@Entity(
    tableName = "exercise_instruction",
    primaryKeys = ["exercise_id", "language"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("language")],
)
data class ExerciseInstructionEntity(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    /** An IETF language tag: `en` or `tr`. Maps to `Language.tag`. */
    @ColumnInfo(name = "language")
    val language: String,

    @ColumnInfo(name = "summary")
    val summary: String,
)

/**
 * Ordered steps, one row each.
 *
 * Stored as rows rather than a serialised list so the ordering is data the
 * database enforces, and so the module needs no TypeConverter at all — every
 * converter is a place where a silent parse failure can hide.
 */
@Entity(
    tableName = "exercise_instruction_step",
    primaryKeys = ["exercise_id", "language", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseInstructionEntity::class,
            parentColumns = ["exercise_id", "language"],
            childColumns = ["exercise_id", "language"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExerciseInstructionStepEntity(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    @ColumnInfo(name = "language")
    val language: String,

    /** Zero-based, contiguous. The import task rejects gaps. */
    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "text")
    val text: String,
)
