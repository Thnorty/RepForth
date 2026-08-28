package com.repforth.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.repforth.core.database.entity.SessionExerciseEntity
import com.repforth.core.database.entity.SetRecordEntity
import com.repforth.core.database.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

data class SessionExerciseWithSets(
    @Embedded val exercise: SessionExerciseEntity,

    @Relation(parentColumn = "id", entityColumn = "session_exercise_id")
    val sets: List<SetRecordEntity>,
)

data class SessionWithDetails(
    @Embedded val session: WorkoutSessionEntity,

    @Relation(entity = SessionExerciseEntity::class, parentColumn = "id", entityColumn = "session_id")
    val exercises: List<SessionExerciseWithSets>,
)

@Dao
interface SessionDao {

    /**
     * The session still in progress, if any.
     *
     * There is at most one: §10 has a single active workout, and starting a
     * second while one runs is a product decision nobody has asked for. Ordered
     * anyway so a stale row from a crash cannot make the result arbitrary.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_session
        WHERE state NOT IN ('COMPLETED', 'ABANDONED')
        ORDER BY started_at DESC LIMIT 1
        """,
    )
    fun observeActive(): Flow<SessionWithDetails?>

    @Transaction
    @Query(
        """
        SELECT * FROM workout_session
        WHERE state NOT IN ('COMPLETED', 'ABANDONED')
        ORDER BY started_at DESC LIMIT 1
        """,
    )
    suspend fun findActive(): SessionWithDetails?

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun findById(id: String): SessionWithDetails?

    /** History, newest first. */
    @Transaction
    @Query("SELECT * FROM workout_session WHERE state = 'COMPLETED' ORDER BY ended_at DESC")
    fun observeCompleted(): Flow<List<SessionWithDetails>>

    /**
     * Writes a whole session state in one transaction.
     *
     * §10 requires every meaningful transition to be persisted transactionally
     * before anything else observes it. Writing the session row, its exercises
     * and its set records together is what makes a process death mid-set leave
     * either the old state or the new one, never half of each.
     */
    @Transaction
    suspend fun persist(
        session: WorkoutSessionEntity,
        exercises: List<SessionExerciseEntity>,
        sets: List<SetRecordEntity>,
    ) {
        upsertSession(session)
        insertExercises(exercises)
        insertSets(sets)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(rows: List<SessionExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(rows: List<SetRecordEntity>)

    @Query("DELETE FROM workout_session")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM workout_session WHERE state = 'COMPLETED'")
    suspend fun completedCount(): Int
}
