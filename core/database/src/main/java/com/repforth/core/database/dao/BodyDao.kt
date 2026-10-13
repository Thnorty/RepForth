package com.repforth.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.repforth.core.database.entity.CheckInEntity
import com.repforth.core.database.entity.MeasurementEntity
import com.repforth.core.database.entity.MeasurementTypeEntity
import com.repforth.core.database.entity.ProgressPhotoEntity
import kotlinx.coroutines.flow.Flow

data class CheckInWithDetails(
    @Embedded val checkIn: CheckInEntity,
    @Relation(parentColumn = "id", entityColumn = "check_in_id")
    val measurements: List<MeasurementEntity>,
    @Relation(parentColumn = "id", entityColumn = "check_in_id")
    val photos: List<ProgressPhotoEntity>,
)

@Dao
interface BodyDao {

    @Query("SELECT * FROM measurement_type ORDER BY sort_order, created_at")
    fun observeTypes(): Flow<List<MeasurementTypeEntity>>

    @Query("SELECT * FROM measurement_type ORDER BY sort_order, created_at")
    suspend fun types(): List<MeasurementTypeEntity>

    /** Seeding: a row that already exists keeps whatever the user made of it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTypesIfMissing(types: List<MeasurementTypeEntity>)

    @Upsert
    suspend fun upsertType(type: MeasurementTypeEntity)

    @Query("UPDATE measurement_type SET tracked = :tracked, updated_at = :now WHERE id = :id")
    suspend fun setTracked(id: String, tracked: Boolean, now: Long)

    @Query("UPDATE measurement_type SET custom_name = :name, updated_at = :now WHERE id = :id")
    suspend fun renameType(id: String, name: String, now: Long)

    @Query("DELETE FROM measurement_type WHERE id = :id")
    suspend fun deleteType(id: String)

    @Query("SELECT COUNT(*) FROM measurement WHERE type_id = :typeId")
    suspend fun countMeasurements(typeId: String): Int

    @Transaction
    @Query("SELECT * FROM check_in ORDER BY epoch_day DESC")
    fun observeCheckIns(): Flow<List<CheckInWithDetails>>

    @Transaction
    @Query("SELECT * FROM check_in ORDER BY epoch_day DESC")
    suspend fun checkIns(): List<CheckInWithDetails>

    @Transaction
    @Query("SELECT * FROM check_in WHERE id = :id")
    suspend fun findCheckIn(id: String): CheckInWithDetails?

    @Transaction
    @Query("SELECT * FROM check_in WHERE epoch_day = :epochDay")
    suspend fun findCheckInOnDay(epochDay: Long): CheckInWithDetails?

    /**
     * Not `@Upsert`. Room's upsert answers a conflict on the unique day by
     * updating the row with this id, which does not exist, so a second check-in
     * on a taken day vanished without an error. A plain insert aborts instead.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCheckIn(checkIn: CheckInEntity)

    @Update
    suspend fun updateCheckIn(checkIn: CheckInEntity): Int

    @Query("DELETE FROM measurement WHERE check_in_id = :checkInId")
    suspend fun deleteMeasurementsOf(checkInId: String)

    @Query("DELETE FROM progress_photo WHERE check_in_id = :checkInId")
    suspend fun deletePhotosOf(checkInId: String)

    @Insert
    suspend fun insertMeasurements(measurements: List<MeasurementEntity>)

    @Insert
    suspend fun insertPhotos(photos: List<ProgressPhotoEntity>)

    /** The check-in and everything on it, replaced as one write. */
    @Transaction
    suspend fun saveCheckIn(
        checkIn: CheckInEntity,
        measurements: List<MeasurementEntity>,
        photos: List<ProgressPhotoEntity>,
    ) {
        if (updateCheckIn(checkIn) == 0) insertCheckIn(checkIn)
        deleteMeasurementsOf(checkIn.id)
        deletePhotosOf(checkIn.id)
        insertMeasurements(measurements)
        insertPhotos(photos)
    }

    @Query("DELETE FROM check_in WHERE id = :id")
    suspend fun deleteCheckIn(id: String)

    @Query("SELECT file_name FROM progress_photo")
    suspend fun photoFileNames(): List<String>

    @Query("DELETE FROM check_in")
    suspend fun deleteAllCheckIns()

    @Query("DELETE FROM measurement_type")
    suspend fun deleteAllTypes()

    /** Every body table. Children go with their check-ins and types. */
    @Transaction
    suspend fun deleteAllBody() {
        deleteAllCheckIns()
        deleteAllTypes()
    }
}
