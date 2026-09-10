package com.repforth.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.repforth.core.database.entity.ProfileEquipmentEntity
import com.repforth.core.database.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * A profile with the membership set that belongs to it.
 *
 * There were three. Preferred muscles and movement exclusions were removed on
 * 2026-09-10 along with the settings that filled them; equipment is the one
 * that remains.
 */
data class ProfileWithDetails(
    @Embedded val profile: UserProfileEntity,

    @Relation(parentColumn = "id", entityColumn = "profile_id")
    val equipment: List<ProfileEquipmentEntity>,
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
     * Replaces the profile and its membership in one transaction.
     *
     * Membership is deleted and re-inserted rather than diffed: the set is tens
     * of rows, a diff would be more code than it saves, and a half-applied
     * profile is a wrong constraint rather than a slow one.
     */
    @Transaction
    suspend fun replaceProfile(
        profile: UserProfileEntity,
        equipment: List<ProfileEquipmentEntity>,
    ) {
        upsertProfile(profile)
        clearEquipment(profile.id)
        insertEquipment(equipment)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquipment(rows: List<ProfileEquipmentEntity>)

    @Query("DELETE FROM profile_equipment WHERE profile_id = :profileId")
    suspend fun clearEquipment(profileId: String)

    /** Used by "reset app" (§7). The catalog is untouched. */
    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()
}
