package com.repforth.core.exercisedata

import com.repforth.core.database.dao.ExerciseDao
import com.repforth.core.database.dao.ExerciseCandidateRow
import com.repforth.core.database.dao.ExerciseSummaryRow
import com.repforth.core.database.mapping.toDomain
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.Muscle
import com.repforth.core.media.MediaResolver
import com.repforth.core.media.PlaceholderMediaResolver
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Reads the prepackaged catalog.
 *
 * `internal`, and bound to the interface by Hilt: nothing outside this module
 * can reach a DAO, which is what stops storage details leaking into features.
 */
internal class RoomExerciseRepository @Inject constructor(
    private val dao: ExerciseDao,
    private val mediaResolver: MediaResolver = PlaceholderMediaResolver(),
) : ExerciseRepository {

    override suspend fun count(): Int = dao.count()

    override fun observeCatalog(filter: CatalogFilter): Flow<List<ExerciseSummary>> {
        val muscles = filter.muscleSlugs()
        return dao.observeCatalog(
            query = filter.query.trim(),
            bodyPart = filter.bodyPart?.slug,
            equipment = filter.equipment?.slug,
            muscles = muscles,
            // SQL has no empty `IN`, so an unset muscle filter is a flag rather
            // than an empty list. See ExerciseDao.observeCatalog.
            ignoreMuscles = muscles.isEmpty(),
        ).map { rows ->
            rows.map { row ->
                val id = ExerciseId(row.id)
                row.toSummary().copy(thumbnail = mediaResolver.resolveThumbnail(id))
            }
        }
    }

    override suspend fun find(id: ExerciseId): Exercise? {
        val row = dao.findById(id.value) ?: return null
        val thumb = mediaResolver.resolveThumbnail(id)
        val anim = mediaResolver.resolveAnimation(id)
        return row.toDomain(thumbnail = thumb, animation = anim)
    }

    override suspend fun summaries(
        ids: Collection<ExerciseId>,
    ): Map<ExerciseId, ExerciseSummary> {
        // SQLite caps the variables in one statement, and a plan is small, but
        // an import could hand this a long list. Chunking keeps the contract the
        // same for any size rather than failing at a threshold nobody tested.
        if (ids.isEmpty()) return emptyMap()
        return ids.map { it.value }
            .distinct()
            .chunked(SQLITE_VARIABLE_LIMIT)
            .flatMap { chunk -> dao.summariesFor(chunk) }
            .associate { row ->
                val id = ExerciseId(row.id)
                id to row.toSummary().copy(thumbnail = mediaResolver.resolveThumbnail(id))
            }
    }

    /**
     * Two queries and a join in memory, rather than one query with a join.
     *
     * A LEFT JOIN onto the secondary-muscle table would return one row per
     * muscle and leave this reassembling the groups anyway, over roughly four
     * times the rows. Reading both tables flat and grouping once is less work
     * and says plainly what it is doing.
     */
    override suspend fun candidates(): List<ExerciseCandidate> {
        val secondaryByExercise = dao.allSecondaryMuscles()
            .groupBy(
                keySelector = { it.exerciseId },
                valueTransform = { Muscle.fromSlug(it.muscle) ?: error("Unknown muscle '${it.muscle}'") },
            )
        return dao.candidates().map { row -> row.toCandidate(secondaryByExercise[row.id].orEmpty()) }
    }

    private companion object {
        /** SQLite's default parameter ceiling is 999; this stays well clear. */
        const val SQLITE_VARIABLE_LIMIT = 500
    }
}

/**
 * A slug with no constant means the packaged catalog and this build disagree
 * about the vocabulary — a packaging fault, caught at import, so failing here
 * is louder and more useful than dropping the row from the list.
 */
private fun ExerciseSummaryRow.toSummary() = ExerciseSummary(
    id = ExerciseId(id),
    name = name,
    bodyPart = BodyPart.fromSlug(bodyPart) ?: error("Unknown body part '$bodyPart'"),
    target = Muscle.fromSlug(target) ?: error("Unknown muscle '$target'"),
    equipment = Equipment.fromSlug(equipment) ?: error("Unknown equipment '$equipment'"),
)

private fun ExerciseCandidateRow.toCandidate(secondary: List<Muscle>) = ExerciseCandidate(
    id = ExerciseId(id),
    name = name,
    bodyPart = BodyPart.fromSlug(bodyPart) ?: error("Unknown body part '$bodyPart'"),
    target = Muscle.fromSlug(target) ?: error("Unknown muscle '$target'"),
    muscleGroup = Muscle.fromSlug(muscleGroup) ?: error("Unknown muscle '$muscleGroup'"),
    secondaryMuscles = secondary.toSet(),
    equipment = Equipment.fromSlug(equipment) ?: error("Unknown equipment '$equipment'"),
)
