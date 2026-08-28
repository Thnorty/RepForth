package com.repforth.core.userdata

import com.repforth.core.common.time.TimeSource
import com.repforth.core.database.dao.SessionDao
import com.repforth.core.database.dao.SessionExerciseWithSets
import com.repforth.core.database.dao.SessionWithDetails
import com.repforth.core.database.entity.SessionExerciseEntity
import com.repforth.core.database.entity.SetRecordEntity
import com.repforth.core.database.entity.WorkoutSessionEntity
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.workout.SessionEngine
import com.repforth.core.workout.SessionExercise
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import com.repforth.core.workout.SetOutcome
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomSessionRepository @Inject constructor(
    private val dao: SessionDao,
    private val time: TimeSource,
) : SessionRepository {

    private val engine = SessionEngine(time)

    override fun observeActive(): Flow<SessionSnapshot?> =
        dao.observeActive().map { it?.toSnapshot() }

    override suspend fun restoreActive(): SessionSnapshot? {
        val row = dao.findActive() ?: return null
        return engine.restore(row.toSnapshot(), row.session.deadlineAt)
    }

    override fun observeCompleted(): Flow<List<SessionSnapshot>> =
        dao.observeCompleted().map { rows -> rows.map(SessionWithDetails::toSnapshot) }

    override suspend fun persist(snapshot: SessionSnapshot) {
        val now = time.now()
        val createdAt = dao.findById(snapshot.sessionId)?.session?.createdAt ?: now

        dao.persist(
            session = WorkoutSessionEntity(
                id = snapshot.sessionId,
                templateId = snapshot.templateId,
                state = snapshot.phase.name,
                phaseBeforePause = snapshot.phaseBeforePause?.name,
                // Stored as wall clock. The monotonic deadline the engine runs on
                // means nothing after a restart, so what survives is the instant
                // the rest is due, and restore converts it back.
                deadlineAt = snapshot.restEndsAtElapsed?.let {
                    now + (it - time.elapsedRealtime()).coerceAtLeast(0)
                },
                startedAt = snapshot.startedAt,
                endedAt = snapshot.endedAt,
                revision = snapshot.revision,
                createdAt = createdAt,
                updatedAt = now,
            ),
            exercises = snapshot.exercises.map { exercise ->
                val reps = exercise.target as? ExerciseTarget.Reps
                val duration = exercise.target as? ExerciseTarget.Duration
                SessionExerciseEntity(
                    id = exercise.id,
                    sessionId = snapshot.sessionId,
                    exerciseId = exercise.exerciseId.value,
                    position = exercise.position,
                    targetSets = exercise.target.sets,
                    targetReps = reps?.reps,
                    targetDurationMs = duration?.durationMs,
                    targetWeightKg = exercise.target.weightKg,
                    restMs = exercise.restMs,
                    createdAt = createdAt,
                    updatedAt = now,
                )
            },
            sets = snapshot.exercises.flatMap { exercise ->
                exercise.sets.map { outcome ->
                    SetRecordEntity(
                        // Deterministic id, so persisting the same snapshot twice
                        // replaces the row rather than duplicating the set. That
                        // is what makes a write retried after a crash safe.
                        id = exercise.id + ":" + outcome.position,
                        sessionExerciseId = exercise.id,
                        position = outcome.position,
                        outcome = if (outcome.skipped) SKIPPED else COMPLETED,
                        reps = outcome.reps,
                        weightKg = outcome.weightKg,
                        durationMs = outcome.durationMs,
                        rpe = outcome.rpe,
                        recordedAt = outcome.recordedAt,
                        createdAt = outcome.recordedAt,
                        updatedAt = now,
                    )
                }
            },
        )
    }

    override suspend fun deleteAll() = dao.deleteAll()

    private companion object {
        const val COMPLETED = "COMPLETED"
        const val SKIPPED = "SKIPPED"
    }
}

private fun SessionWithDetails.toSnapshot(): SessionSnapshot {
    val ordered = exercises.sortedBy { it.exercise.position }

    // Where the user is, derived rather than stored: the first exercise still
    // owed sets. Deriving it means the cursor cannot disagree with the set
    // records, which is the kind of drift a crash mid-write would otherwise
    // leave behind.
    val exerciseIndex = ordered
        .indexOfFirst { it.sets.size < it.exercise.targetSets }
        .takeIf { it >= 0 }
        ?: ordered.lastIndex.coerceAtLeast(0)

    return SessionSnapshot(
        sessionId = session.id,
        templateId = session.templateId,
        phase = SessionPhase.entries.firstOrNull { it.name == session.state }
            ?: SessionPhase.ABANDONED,
        phaseBeforePause = session.phaseBeforePause?.let { name ->
            SessionPhase.entries.firstOrNull { it.name == name }
        },
        exercises = ordered.map(SessionExerciseWithSets::toDomain),
        currentExerciseIndex = exerciseIndex,
        currentSetIndex = ordered.getOrNull(exerciseIndex)?.sets?.size ?: 0,
        startedAt = session.startedAt,
        endedAt = session.endedAt,
        revision = session.revision,
    )
}

private fun SessionExerciseWithSets.toDomain() = SessionExercise(
    id = exercise.id,
    exerciseId = ExerciseId(exercise.exerciseId),
    position = exercise.position,
    target = exercise.toTarget(),
    restMs = exercise.restMs,
    sets = sets.sortedBy { it.position }.map { set ->
        SetOutcome(
            position = set.position,
            skipped = set.outcome == "SKIPPED",
            reps = set.reps,
            weightKg = set.weightKg,
            durationMs = set.durationMs,
            rpe = set.rpe,
            recordedAt = set.recordedAt,
        )
    },
)

/** Reps wins if a row somehow has both; the schema allows it and the type does not. */
private fun SessionExerciseEntity.toTarget(): ExerciseTarget {
    val reps = targetReps
    val duration = targetDurationMs
    return when {
        reps != null -> ExerciseTarget.Reps(targetSets, reps, targetWeightKg)
        duration != null -> ExerciseTarget.Duration(targetSets, duration, targetWeightKg)
        else -> ExerciseTarget.Reps(targetSets, reps = 1, weightKg = targetWeightKg)
    }
}
