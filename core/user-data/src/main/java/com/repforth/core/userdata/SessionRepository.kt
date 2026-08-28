package com.repforth.core.userdata

import com.repforth.core.workout.SessionSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Durable storage for a workout in progress, and for the ones that finished.
 *
 * The engine is pure and holds nothing; this is where its snapshots live. §10
 * requires a transition to be persisted before anything else observes it, so
 * [persist] is called with the state the engine returned, and only once it
 * succeeds does anyone act on it.
 */
interface SessionRepository {

    /** The workout in progress, or null. At most one exists. */
    fun observeActive(): Flow<SessionSnapshot?>

    /**
     * The workout in progress, with its rest deadline rebuilt.
     *
     * Used on cold start. Monotonic time is meaningless across a restart, so the
     * stored wall-clock deadline is converted back to a monotonic one here
     * rather than by every caller.
     */
    suspend fun restoreActive(): SessionSnapshot?

    fun observeCompleted(): Flow<List<SessionSnapshot>>

    suspend fun persist(snapshot: SessionSnapshot)

    /** "Delete all workout data" (§7). The bundled catalog is untouched. */
    suspend fun deleteAll()
}
