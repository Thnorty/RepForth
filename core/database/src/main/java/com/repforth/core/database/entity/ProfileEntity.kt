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
 * recommended rather than exhaustive; this and [ProfilePreferredMuscleEntity] are
 * the two it does not name but the onboarding questions in §3 require.
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

/** Muscles the user wants emphasised. Exclusions live in [MovementExclusionEntity]. */
@Entity(
    tableName = "profile_preferred_muscle",
    primaryKeys = ["profile_id", "muscle"],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("muscle")],
)
data class ProfilePreferredMuscleEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "muscle")
    val muscle: String,
)

/**
 * Things never to program for this user (§7): a specific exercise, a muscle, or a
 * movement pattern.
 *
 * One table with a `kind` discriminator rather than three, because every consumer
 * — the rules engine, and the AI validator in Phase 2 — wants the whole set at
 * once and would otherwise union three queries.
 *
 * These are hard constraints. §8 requires that no generated plan can ever violate
 * one, which is why this is data the engine reads rather than a UI preference.
 */
@Entity(
    tableName = "movement_exclusion",
    primaryKeys = ["profile_id", "kind", "value"],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("kind"), Index("value")],
)
data class MovementExclusionEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: String,

    /** `EXERCISE`, `MUSCLE` or `MOVEMENT`. */
    @ColumnInfo(name = "kind")
    val kind: String,

    /** An exercise id, a muscle slug, or a movement name, per [kind]. */
    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
