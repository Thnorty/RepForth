package com.repforth.core.userdata

import com.repforth.core.model.TrainingWeek
import kotlinx.coroutines.flow.Flow

/** Saved training weeks (§3). */
interface WeekRepository {

    fun observeAll(): Flow<List<TrainingWeek>>

    fun observeActive(): Flow<TrainingWeek?>

    suspend fun find(id: String): TrainingWeek?

    /**
     * Saves the week and all its day templates and exercises in one
     * transaction (§3.3).
     */
    suspend fun save(week: TrainingWeek)

    /** Sets [id] as the single active week. */
    suspend fun setActive(id: String)

    /** Removes the week. Member templates are cascade-deleted. */
    suspend fun delete(id: String)

    /** "Delete all workout data" (§7). */
    suspend fun deleteAll()
}
