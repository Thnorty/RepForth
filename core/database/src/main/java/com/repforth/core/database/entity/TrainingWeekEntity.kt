package com.repforth.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A week of training (§3).
 *
 * A week is a container of workouts, not a new kind of workout. Workouts
 * belonging to this week reference [id] via [WorkoutTemplateEntity.weekId].
 */
@Entity(tableName = "training_week")
data class TrainingWeekEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "notes")
    val notes: String?,

    /** `MANUAL` or `AI`. */
    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "active")
    val active: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
