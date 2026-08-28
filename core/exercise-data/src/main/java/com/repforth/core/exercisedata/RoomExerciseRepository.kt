package com.repforth.core.exercisedata

import com.repforth.core.database.dao.ExerciseDao
import com.repforth.core.database.dao.ExerciseSummaryRow
import com.repforth.core.database.mapping.toDomain
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.Muscle
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
        ).map { rows -> rows.map(ExerciseSummaryRow::toSummary) }
    }

    override suspend fun find(id: ExerciseId): Exercise? =
        dao.findById(id.value)?.toDomain()
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
