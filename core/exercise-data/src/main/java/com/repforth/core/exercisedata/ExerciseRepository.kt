package com.repforth.core.exercisedata

import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import kotlinx.coroutines.flow.Flow

/**
 * The catalog, as the rest of the app sees it.
 *
 * An interface because the rules engine and the AI validator (§8) both need to
 * ask the catalog questions in tests without a database, and because it is the
 * seam where media resolution will be joined in later (§9) without any caller
 * noticing.
 */
interface ExerciseRepository {

    /** How many exercises are packaged. Cheap; used to prove the asset loaded. */
    suspend fun count(): Int

    /** The catalog list, narrowed by [filter] and re-emitted when it changes. */
    fun observeCatalog(filter: CatalogFilter): Flow<List<ExerciseSummary>>

    /** Everything about one exercise, both languages included. */
    suspend fun find(id: ExerciseId): Exercise?
}
