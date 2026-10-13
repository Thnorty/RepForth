package com.repforth.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Something a person can measure: a built-in or one of their own.
 *
 * Built-ins use their fixed id (`weight`, `waist`, …) and take their names from
 * string resources, so [customName] is null for them. Rows are seeded by the
 * repository, not by a migration, because a fresh install starts from the
 * prepackaged asset rather than migrating.
 */
@Entity(tableName = "measurement_type")
data class MeasurementTypeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** The `BuiltInMeasurement` id, or null for a custom type. */
    @ColumnInfo(name = "built_in")
    val builtIn: String?,

    @ColumnInfo(name = "custom_name")
    val customName: String?,

    /** `MASS`, `LENGTH`, `PERCENT` or `PLAIN`. */
    @ColumnInfo(name = "kind")
    val kind: String,

    /** `PLAIN` only: the user's own unit, never converted. */
    @ColumnInfo(name = "unit_label")
    val unitLabel: String?,

    @ColumnInfo(name = "tracked")
    val tracked: Boolean,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/** One day's record of the body. One per local date, which the unique index holds. */
@Entity(
    tableName = "check_in",
    indices = [Index(value = ["epoch_day"], unique = true)],
)
data class CheckInEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** The local date, as `LocalDate.toEpochDay()`. A date, not an instant. */
    @ColumnInfo(name = "epoch_day")
    val epochDay: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/**
 * One value on one check-in.
 *
 * [value] is canonical — kilograms, centimetres or percent, decided by the
 * type's kind — and so cannot carry a unit in its name the way `weight_kg` does.
 */
@Entity(
    tableName = "measurement",
    foreignKeys = [
        ForeignKey(
            entity = CheckInEntity::class,
            parentColumns = ["id"],
            childColumns = ["check_in_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MeasurementTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["check_in_id", "type_id"], unique = true),
        Index(value = ["type_id"]),
    ],
)
data class MeasurementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "check_in_id")
    val checkInId: String,

    @ColumnInfo(name = "type_id")
    val typeId: String,

    @ColumnInfo(name = "value")
    val value: Double,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/** A photo on a check-in: at most one per pose. The file lives in app-private storage. */
@Entity(
    tableName = "progress_photo",
    foreignKeys = [
        ForeignKey(
            entity = CheckInEntity::class,
            parentColumns = ["id"],
            childColumns = ["check_in_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["check_in_id", "pose"], unique = true)],
)
data class ProgressPhotoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "check_in_id")
    val checkInId: String,

    /** `FRONT`, `SIDE` or `BACK`. */
    @ColumnInfo(name = "pose")
    val pose: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
