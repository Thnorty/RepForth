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

    /**
     * Names and facets for a known set of ids, keyed by id.
     *
     * A plan stores exercise ids and nothing else, so anything that renders one
     * has to resolve names. Returning a map rather than a list keeps the
     * plan's own order the only order in play, and lets a caller notice an id
     * the catalog no longer has — which a dataset update can produce, and which
     * a list would silently shorten.
     */
    suspend fun summaries(ids: Collection<ExerciseId>): Map<ExerciseId, ExerciseSummary>
}
