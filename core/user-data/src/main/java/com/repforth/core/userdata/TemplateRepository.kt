package com.repforth.core.userdata

import com.repforth.core.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

/** Saved plans (§7). */
interface TemplateRepository {

    fun observeAll(): Flow<List<WorkoutTemplate>>

    suspend fun find(id: String): WorkoutTemplate?

    suspend fun save(template: WorkoutTemplate)

    /** Removes the plan. Sessions performed from it are kept. */
    suspend fun delete(id: String)

    /** "Delete all workout data" (§7). The bundled catalog is untouched. */
    suspend fun deleteAll()
}
