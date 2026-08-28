package com.repforth.core.userdata

import com.repforth.core.common.time.TimeSource
import com.repforth.core.database.dao.ProfileDao
import com.repforth.core.database.dao.ProfileWithDetails
import com.repforth.core.database.entity.MovementExclusionEntity
import com.repforth.core.database.entity.ProfileEquipmentEntity
import com.repforth.core.database.entity.ProfilePreferredMuscleEntity
import com.repforth.core.database.entity.UserProfileEntity
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val time: TimeSource,
) : ProfileRepository {

    override fun observeProfile(): Flow<UserProfile?> =
        dao.observeProfile().map { it?.toDomain() }

    override suspend fun getProfile(): UserProfile? = dao.findProfile()?.toDomain()

    override suspend fun save(profile: UserProfile) {
        val now = time.now()
        // `created_at` is preserved when a profile already exists, so editing
        // equipment does not make the account look newly created.
        val createdAt = dao.findProfile()?.profile?.createdAt ?: now

        dao.replaceProfile(
            profile = UserProfileEntity(
                id = profile.id,
                goal = profile.goal.name,
                experience = profile.experience.name,
                trainingDaysPerWeek = profile.trainingDaysPerWeek,
                sessionLengthMs = profile.sessionLengthMs,
                createdAt = createdAt,
                updatedAt = now,
            ),
            equipment = profile.availableEquipment.map {
                ProfileEquipmentEntity(profile.id, it.slug)
            },
            preferredMuscles = profile.preferredMuscles.map {
                ProfilePreferredMuscleEntity(profile.id, it.slug)
            },
            exclusions = profile.exclusions.map {
                MovementExclusionEntity(profile.id, it.kind.name, it.value, now)
            },
        )
    }

    override suspend fun deleteAll() = dao.deleteAll()
}

/**
 * Unknown stored values are dropped rather than thrown on.
 *
 * A profile is not the catalog: its rows were written by an older version of this
 * app, so a slug with no constant means the vocabulary moved under an existing
 * user. Losing one piece of equipment from a filter is recoverable; refusing to
 * load the profile at all would lock them out of their own app.
 */
private fun ProfileWithDetails.toDomain() = UserProfile(
    id = profile.id,
    goal = enumOrDefault(profile.goal, TrainingGoal.GENERAL_FITNESS),
    experience = enumOrDefault(profile.experience, ExperienceLevel.BEGINNER),
    trainingDaysPerWeek = profile.trainingDaysPerWeek,
    sessionLengthMs = profile.sessionLengthMs,
    availableEquipment = equipment.mapNotNullTo(mutableSetOf()) { Equipment.fromSlug(it.equipment) },
    preferredMuscles = preferredMuscles.mapNotNullTo(mutableSetOf()) { Muscle.fromSlug(it.muscle) },
    exclusions = exclusions.mapNotNullTo(mutableSetOf()) { row ->
        ExclusionKind.entries.firstOrNull { it.name == row.kind }
            ?.let { MovementExclusion(it, row.value) }
    },
)

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback
