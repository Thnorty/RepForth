package com.repforth.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One language's instructions for one exercise: ordered steps, one row each.
 *
 * Rows rather than a serialised list, so ordering is data the database enforces
 * and the module needs no TypeConverter at all — every converter is a place a
 * silent parse failure can hide.
 *
 * There is no companion `exercise_instruction` table. It would have held a
 * summary, but upstream's `instructions` field is exactly the steps joined with
 * a space for all 1,324 records, so the table had no payload left and the rows
 * here already say which languages an exercise has.
 *
 * Language is a column rather than a suffixed pair (`text_en`, `text_tr`)
 * because §13 treats the two languages as equals. A schema naming them makes
 * English structurally privileged and a third language a migration, not a row.
 */
@Entity(
    tableName = "exercise_instruction_step",
    primaryKeys = ["exercise_id", "language", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exercise_id"), Index("language")],
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
