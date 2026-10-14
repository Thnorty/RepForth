package com.repforth.core.body

import android.net.Uri
import com.repforth.core.database.dao.BodyDao
import com.repforth.core.database.dao.CheckInWithDetails
import com.repforth.core.database.entity.CheckInEntity
import com.repforth.core.database.entity.MeasurementEntity
import com.repforth.core.database.entity.MeasurementTypeEntity
import com.repforth.core.database.entity.ProgressPhotoEntity
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The body tables in memory, holding the constraints SQLite holds: one check-in
 * per day, and cascades from check-ins and types. `BodyDaoTest` proves the real
 * ones on a device; this lets the repository's own rules run on the JVM.
 */
internal class FakeBodyDao : BodyDao {

    private val types = MutableStateFlow<Map<String, MeasurementTypeEntity>>(emptyMap())
    private val checkIns = MutableStateFlow<Map<String, CheckInEntity>>(emptyMap())
    private val measurements = mutableListOf<MeasurementEntity>()
    private val photos = mutableListOf<ProgressPhotoEntity>()

    private fun sortedTypes() = types.value.values.sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))

    private fun details(entity: CheckInEntity) = CheckInWithDetails(
        entity,
        measurements.filter { it.checkInId == entity.id },
        photos.filter { it.checkInId == entity.id },
    )

    private fun sortedCheckIns() = checkIns.value.values.sortedByDescending { it.epochDay }.map(::details)

    private fun touch() {
        checkIns.value = checkIns.value.toMap()
    }

    override fun observeTypes(): Flow<List<MeasurementTypeEntity>> = types.map { sortedTypes() }

    override suspend fun types(): List<MeasurementTypeEntity> = sortedTypes()

    override suspend fun insertTypesIfMissing(types: List<MeasurementTypeEntity>) {
        this.types.value = this.types.value + types.filterNot { it.id in this.types.value }.associateBy { it.id }
    }

    override suspend fun upsertType(type: MeasurementTypeEntity) {
        types.value = types.value + (type.id to type)
    }

    override suspend fun setTracked(id: String, tracked: Boolean, now: Long) {
        types.value[id]?.let { upsertType(it.copy(tracked = tracked, updatedAt = now)) }
    }

    override suspend fun renameType(id: String, name: String, now: Long) {
        types.value[id]?.let { upsertType(it.copy(customName = name, updatedAt = now)) }
    }

    override suspend fun deleteType(id: String) {
        measurements.removeAll { it.typeId == id }
        types.value = types.value - id
        touch()
    }

    override suspend fun countMeasurements(typeId: String): Int = measurements.count { it.typeId == typeId }

    override fun observeCheckIns(): Flow<List<CheckInWithDetails>> = checkIns.map { sortedCheckIns() }

    override suspend fun checkIns(): List<CheckInWithDetails> = sortedCheckIns()

    override suspend fun findCheckIn(id: String): CheckInWithDetails? = checkIns.value[id]?.let(::details)

    override suspend fun findCheckInOnDay(epochDay: Long): CheckInWithDetails? =
        checkIns.value.values.firstOrNull { it.epochDay == epochDay }?.let(::details)

    override suspend fun insertCheckIn(checkIn: CheckInEntity) {
        check(checkIn.id !in checkIns.value) { "duplicate id" }
        check(checkIns.value.values.none { it.epochDay == checkIn.epochDay }) { "UNIQUE constraint failed: epoch_day" }
        checkIns.value = checkIns.value + (checkIn.id to checkIn)
    }

    override suspend fun updateCheckIn(checkIn: CheckInEntity): Int {
        if (checkIn.id !in checkIns.value) return 0
        check(checkIns.value.values.none { it.epochDay == checkIn.epochDay && it.id != checkIn.id }) {
            "UNIQUE constraint failed: epoch_day"
        }
        checkIns.value = checkIns.value + (checkIn.id to checkIn)
        return 1
    }

    override suspend fun deleteMeasurementsOf(checkInId: String) {
        measurements.removeAll { it.checkInId == checkInId }
    }

    override suspend fun deletePhotosOf(checkInId: String) {
        photos.removeAll { it.checkInId == checkInId }
    }

    override suspend fun insertMeasurements(measurements: List<MeasurementEntity>) {
        measurements.forEach { m ->
            check(m.checkInId in checkIns.value && m.typeId in types.value) { "FOREIGN KEY constraint failed" }
        }
        this.measurements += measurements
        touch()
    }

    override suspend fun insertPhotos(photos: List<ProgressPhotoEntity>) {
        this.photos += photos
        touch()
    }

    override suspend fun deleteCheckIn(id: String) {
        measurements.removeAll { it.checkInId == id }
        photos.removeAll { it.checkInId == id }
        checkIns.value = checkIns.value - id
    }

    override suspend fun photoFileNames(): List<String> = photos.map { it.fileName }

    override suspend fun deleteAllCheckIns() {
        measurements.clear()
        photos.clear()
        checkIns.value = emptyMap()
    }

    override suspend fun deleteAllTypes() {
        measurements.clear()
        types.value = emptyMap()
    }
}

/** Records what the repository asked of the photo files. */
internal class RecordingPhotoStore : PhotoStore {
    val deleted = mutableListOf<String>()
    val sweeps = mutableListOf<Set<String>>()
    var deletedAll = 0

    override suspend fun importFrom(source: Uri): PhotoImport = error("not used")
    override fun newCapture(): PhotoCapture = error("not used")
    override suspend fun importCapture(path: String): PhotoImport = error("not used")
    override fun fileFor(fileName: String): File = File(fileName)
    override suspend fun delete(fileNames: Collection<String>) {
        deleted += fileNames
    }
    override suspend fun sweep(referenced: Set<String>) {
        sweeps += referenced
    }
    override suspend fun deleteAll() {
        deletedAll++
    }
}
