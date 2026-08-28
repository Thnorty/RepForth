package com.repforth.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A workout being performed, or one that was (§7, §10).
 *
 * [templateId] is nullable and has no foreign key. A session may be ad-hoc, and
 * more importantly deleting a plan must not delete the history of having trained
 * it — the session is the record of what happened, and it outlives the intention.
 *
 * [revision] increments on every mutation. §10 requires commands to carry an
 * `expectedRevision` and to be idempotent, so that a duplicate or out-of-order
 * command from the watch returns the current state instead of applying twice.
 * The watch does not exist until Phase 4, but retrofitting a revision into a
 * live state machine is far worse than carrying one from the start.
 *
 * [phaseBeforePause] is what makes pause correct: §10's `Paused` state has to
 * remember whether it suspended an active set or a rest, or resuming sends the
 * user to the wrong place.
 */
@Entity(
    tableName = "workout_session",
    indices = [Index("state"), Index("started_at"), Index("template_id")],
)
data class WorkoutSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "template_id")
    val templateId: String?,

    /** `PREPARING`, `ACTIVE`, `RESTING`, `PAUSED`, `COMPLETING`, `COMPLETED`, `ABANDONED`. */
    @ColumnInfo(name = "state")
    val state: String,

    /** Set only while [state] is `PAUSED`; the phase to return to on resume. */
    @ColumnInfo(name = "phase_before_pause")
    val phaseBeforePause: String?,

    /**
     * When the current rest or timed set ends, as epoch milliseconds.
     *
     * Persisted as a wall-clock instant so a restored session knows where it
     * stands. The countdown the user sees is driven from a monotonic clock
     * (§10), because wall-clock time can jump; this is the durable anchor, not
     * the ticking source.
     */
    @ColumnInfo(name = "deadline_at")
    val deadlineAt: Long?,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "ended_at")
    val endedAt: Long?,

    @ColumnInfo(name = "revision")
    val revision: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/**
 * One exercise within a session.
 *
 * A copy of the plan's intent rather than a pointer to it: [targetSets] and the
 * rest are snapshotted at start, so editing a plan afterwards cannot rewrite what
 * a past workout was supposed to be.
 */
@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id"), Index("exercise_id")],
)
data class SessionExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "target_sets")
    val targetSets: Int,

    @ColumnInfo(name = "target_reps")
    val targetReps: Int?,

    @ColumnInfo(name = "target_duration_ms")
    val targetDurationMs: Long?,

    @ColumnInfo(name = "target_weight_kg")
    val targetWeightKg: Double?,

    @ColumnInfo(name = "rest_ms")
    val restMs: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/**
 * What actually happened in one set (§7).
 *
 * A skipped set is a row with [outcome] `SKIPPED`, not a missing row. The
 * difference matters for history: "did four of five sets" and "did four sets" are
 * different facts, and only the first is recoverable if skips are recorded.
 */
@Entity(
    tableName = "set_record",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_exercise_id"), Index("recorded_at")],
)
data class SetRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_exercise_id")
    val sessionExerciseId: String,

    /** Which set within the exercise. Zero-based and contiguous. */
    @ColumnInfo(name = "position")
    val position: Int,

    /** `COMPLETED` or `SKIPPED`. */
    @ColumnInfo(name = "outcome")
    val outcome: String,

    @ColumnInfo(name = "reps")
    val reps: Int?,

    /** Kilograms, always, whatever the display preference was. */
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double?,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,

    /** Rate of perceived exertion, 1–10. Optional; most users never fill it in. */
    @ColumnInfo(name = "rpe")
    val rpe: Int?,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
