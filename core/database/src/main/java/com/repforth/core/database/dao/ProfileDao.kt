package com.repforth.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.repforth.core.database.entity.MovementExclusionEntity
import com.repforth.core.database.entity.ProfileEquipmentEntity
import com.repforth.core.database.entity.ProfilePreferredMuscleEntity
import com.repforth.core.database.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/** A profile with the three membership sets that belong to it. */
data class ProfileWithDetails(
    @Embedded val profile: UserProfileEntity,

    @Relation(parentColumn = "id", entityColumn = "profile_id")
    val equipment: List<ProfileEquipmentEntity>,

    @Relation(parentColumn = "id", entityColumn = "profile_id")
    val preferredMuscles: List<ProfilePreferredMuscleEntity>,

    @Relation(parentColumn = "id", entityColumn = "profile_id")
    val exclusions: List<MovementExclusionEntity>,
)

@Dao
interface ProfileDao {

    /**
     * The profile, or null before onboarding.
     *
     * A Flow because the rules engine and the builder both need to react when a
     * constraint changes — adding a piece of equipment should change what the
     * next plan offers without anyone reloading a screen.
     */
    @Transaction
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun observeProfile(): Flow<ProfileWithDetails?>

    @Transaction
    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun findProfile(): ProfileWithDetails?

    /**
     * Replaces the profile and all three membership sets in one transaction.
     *
     * Membership is deleted and re-inserted rather than diffed: the sets are
     * tens of rows, a diff would be more code than it saves, and a half-applied
     * profile is a wrong constraint rather than a slow one.
     */
    @Transaction
    suspend fun replaceProfile(
        profile: UserProfileEntity,
        equipment: List<ProfileEquipmentEntity>,
        preferredMuscles: List<ProfilePreferredMuscleEntity>,
        exclusions: List<MovementExclusionEntity>,
    ) {
        upsertProfile(profile)
        clearEquipment(profile.id)
        clearPreferredMuscles(profile.id)
        clearExclusions(profile.id)
        insertEquipment(equipment)
        insertPreferredMuscles(preferredMuscles)
        insertExclusions(exclusions)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquipment(rows: List<ProfileEquipmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferredMuscles(rows: List<ProfilePreferredMuscleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExclusions(rows: List<MovementExclusionEntity>)

    @Query("DELETE FROM profile_equipment WHERE profile_id = :profileId")
    suspend fun clearEquipment(profileId: String)

    @Query("DELETE FROM profile_preferred_muscle WHERE profile_id = :profileId")
    suspend fun clearPreferredMuscles(profileId: String)

    @Query("DELETE FROM movement_exclusion WHERE profile_id = :profileId")
    suspend fun clearExclusions(profileId: String)

    /** Used by "reset app" (§7). The catalog is untouched. */
    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()
}
