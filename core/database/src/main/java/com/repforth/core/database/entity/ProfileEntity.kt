package com.repforth.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * User data (§7).
 *
 * Three conventions hold across every table in this file and the two beside it,
 * and they are worth stating once rather than repeating:
 *
 * 1. Mutable rows have a UUID primary key and `created_at`/`updated_at`, stored
 *    as epoch milliseconds UTC.
 * 2. Weights are kilograms and durations are milliseconds, always. Display units
 *    are a preference, and no stored number may depend on what the user had
 *    selected when they logged it.
 * 3. **No foreign key points at the `exercise` table.** Catalog IDs are stored as
 *    plain indexed columns instead. This is deliberate: the catalog is replaced
 *    wholesale when the dataset pin moves, so a CASCADE would silently delete a
 *    user's training history along with a retired exercise, and a RESTRICT would
 *    make the update impossible. A reference to an exercise that no longer exists
 *    is a display problem — show the record, say the exercise is unknown — and it
 *    must never be a data-loss problem.
 */

/**
 * The user's training context, gathered at onboarding (§3).
 *
 * One row in practice, but it carries a UUID like every other mutable table
 * rather than a hardcoded id, so multiple profiles never require a migration.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** `STRENGTH`, `HYPERTROPHY`, … — this project's vocabulary, not upstream's. */
    @ColumnInfo(name = "goal")
    val goal: String,

    @ColumnInfo(name = "experience")
    val experience: String,

    @ColumnInfo(name = "training_days_per_week")
    val trainingDaysPerWeek: Int,

    /** The session length the user is willing to train for, as a ceiling. */
    @ColumnInfo(name = "session_length_ms")
    val sessionLengthMs: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/**
 * What the user can actually train with.
 *
 * A junction table rather than a column, so "which exercises can this person do"
 * is an indexed join instead of a string search. §7 lists its tables as
 * recommended rather than exhaustive, and this is one it does not name.
 */
@Entity(
    tableName = "profile_equipment",
    primaryKeys = ["profile_id", "equipment"],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("equipment")],
)
data class ProfileEquipmentEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: String,

    /** The upstream equipment slug, matching `exercise.equipment`. */
    @ColumnInfo(name = "equipment")
    val equipment: String,
)
