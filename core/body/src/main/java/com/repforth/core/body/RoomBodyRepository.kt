package com.repforth.core.body

import com.repforth.core.common.time.TimeSource
import com.repforth.core.database.dao.BodyDao
import com.repforth.core.database.dao.CheckInWithDetails
import com.repforth.core.database.entity.CheckInEntity
import com.repforth.core.database.entity.MeasurementEntity
import com.repforth.core.database.entity.MeasurementTypeEntity
import com.repforth.core.database.entity.ProgressPhotoEntity
import com.repforth.core.model.BuiltInMeasurement
import com.repforth.core.model.CheckIn
import com.repforth.core.model.CheckInProblem
import com.repforth.core.model.CheckInRules
import com.repforth.core.model.MeasurementKind
import com.repforth.core.model.MeasurementType
import com.repforth.core.model.Pose
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RoomBodyRepository @Inject constructor(
    private val dao: BodyDao,
    private val photos: PhotoStore,
    private val time: TimeSource,
    private val zone: ZoneId,
) : BodyRepository {

    private val seeding = Mutex()

    @Volatile
    private var seeded = false

    private val swept = AtomicBoolean(false)

    /** Inserts any built-in that is missing; a row already there is left as the user made it. */
    private suspend fun ensureSeeded() {
        if (seeded) return
        seeding.withLock {
            if (seeded) return
            val now = time.now()
            dao.insertTypesIfMissing(
                BuiltInMeasurement.entries.mapIndexed { index, builtIn ->
                    MeasurementTypeEntity(
                        id = builtIn.id,
                        builtIn = builtIn.id,
                        customName = null,
                        kind = builtIn.kind.name,
                        unitLabel = null,
                        tracked = builtIn.trackedByDefault,
                        sortOrder = index,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
            seeded = true
        }
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(time.now()).atZone(zone).toLocalDate()

    override fun observeTypes(): Flow<List<MeasurementType>> = flow {
        ensureSeeded()
        emitAll(dao.observeTypes().map { rows -> rows.map { it.toDomain() } })
    }

    override fun observeCheckIns(): Flow<List<CheckIn>> = flow {
        ensureSeeded()
        emitAll(dao.observeCheckIns().map { rows -> rows.map { it.toDomain() } })
    }

    override suspend fun find(id: String): CheckIn? = dao.findCheckIn(id)?.toDomain()

    override suspend fun findOn(date: LocalDate): CheckIn? = dao.findCheckInOnDay(date.toEpochDay())?.toDomain()

    override suspend fun save(checkIn: CheckIn): SaveCheckIn {
        ensureSeeded()
        val types = dao.types().map { it.toDomain() }.associateBy { it.id }
        CheckInRules.check(checkIn, today(), types)?.let { return SaveCheckIn.Refused(it) }

        val sameDay = dao.findCheckInOnDay(checkIn.date.toEpochDay())
        if (sameDay != null && sameDay.checkIn.id != checkIn.id) {
            return SaveCheckIn.Refused(CheckInProblem.DATE_TAKEN)
        }

        val previous = dao.findCheckIn(checkIn.id)
        write(checkIn, previous)

        val kept = checkIn.photos.values.toSet()
        val dropped = previous?.photos.orEmpty().map { it.fileName }.filterNot { it in kept }
        if (dropped.isNotEmpty()) photos.delete(dropped)
        return SaveCheckIn.Saved(checkIn.id)
    }

    /** The rows for [checkIn], keeping creation times from [previous]. */
    private suspend fun write(checkIn: CheckIn, previous: CheckInWithDetails?) {
        val now = time.now()
        val measurementsBefore = previous?.measurements.orEmpty().associateBy { it.typeId }
        val photosBefore = previous?.photos.orEmpty().associateBy { it.pose }
        dao.saveCheckIn(
            CheckInEntity(
                id = checkIn.id,
                epochDay = checkIn.date.toEpochDay(),
                createdAt = previous?.checkIn?.createdAt ?: now,
                updatedAt = now,
            ),
            checkIn.measurements.map { (typeId, value) ->
                MeasurementEntity(
                    id = "${checkIn.id}/$typeId",
                    checkInId = checkIn.id,
                    typeId = typeId,
                    value = value,
                    createdAt = measurementsBefore[typeId]?.createdAt ?: now,
                    updatedAt = now,
                )
            },
            checkIn.photos.map { (pose, fileName) ->
                ProgressPhotoEntity(
                    id = "${checkIn.id}/${pose.name}",
                    checkInId = checkIn.id,
                    pose = pose.name,
                    fileName = fileName,
                    createdAt = photosBefore[pose.name]?.createdAt ?: now,
                    updatedAt = now,
                )
            },
        )
    }

    override suspend fun delete(id: String) {
        val existing = dao.findCheckIn(id) ?: return
        dao.deleteCheckIn(id)
        val files = existing.photos.map { it.fileName }
        if (files.isNotEmpty()) photos.delete(files)
    }

    override suspend fun setTracked(typeId: String, tracked: Boolean) {
        ensureSeeded()
        dao.setTracked(typeId, tracked, time.now())
    }

    override suspend fun addCustom(name: String, kind: MeasurementKind, unitLabel: String?): MeasurementType {
        ensureSeeded()
        val now = time.now()
        val entity = MeasurementTypeEntity(
            id = UUID.randomUUID().toString(),
            builtIn = null,
            customName = name,
            kind = kind.name,
            unitLabel = unitLabel.takeIf { kind == MeasurementKind.PLAIN },
            tracked = true,
            sortOrder = (dao.types().maxOfOrNull { it.sortOrder } ?: -1) + 1,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertType(entity)
        return entity.toDomain()
    }

    private suspend fun customType(typeId: String): MeasurementTypeEntity? =
        dao.types().firstOrNull { it.id == typeId && it.builtIn == null }

    override suspend fun rename(typeId: String, name: String) {
        customType(typeId) ?: return
        dao.renameType(typeId, name, time.now())
    }

    override suspend fun deleteCustom(typeId: String) {
        customType(typeId) ?: return
        dao.deleteType(typeId)
        // A check-in whose only value was that type now holds nothing, which
        // CheckInRules never lets a save create; it goes, unless it has photos.
        dao.checkIns()
            .filter { it.measurements.isEmpty() && it.photos.isEmpty() }
            .forEach { dao.deleteCheckIn(it.checkIn.id) }
    }

    override suspend fun entryCount(typeId: String): Int = dao.countMeasurements(typeId)

    override suspend fun restoreMeasurements(types: List<MeasurementType>, checkIns: List<CheckIn>) {
        ensureSeeded()
        val now = time.now()

        val typesBefore = dao.types().associateBy { it.id }
        types.forEach { type ->
            dao.upsertType(type.toEntity(createdAt = typesBefore[type.id]?.createdAt ?: now, updatedAt = now))
        }
        val fileTypeIds = types.map { it.id }.toSet()
        typesBefore.values
            .filter { it.builtIn == null && it.id !in fileTypeIds }
            .forEach { dao.deleteType(it.id) }

        // Photographed check-ins stay, emptied of measurements; the rest go.
        val photographed = mutableMapOf<LocalDate, CheckInWithDetails>()
        dao.checkIns().forEach { existing ->
            if (existing.photos.isEmpty()) {
                dao.deleteCheckIn(existing.checkIn.id)
            } else {
                val emptied = existing.toDomain().copy(measurements = emptyMap())
                write(emptied, existing)
                photographed[emptied.date] = existing
            }
        }

        val photographedIds = photographed.values.map { it.checkIn.id }.toSet()
        checkIns.forEach { incoming ->
            val onThatDay = photographed[incoming.date]
            when {
                // Same day as a photographed check-in: one per day, so they merge.
                onThatDay != null ->
                    write(onThatDay.toDomain().copy(measurements = incoming.measurements), onThatDay)
                // The file names a photographed check-in that has since moved
                // day. Written under its own id it would drag the row back and
                // empty its photos, so it becomes a check-in of its own.
                incoming.id in photographedIds ->
                    write(incoming.copy(id = UUID.randomUUID().toString(), photos = emptyMap()), previous = null)
                else -> write(incoming.copy(photos = emptyMap()), previous = null)
            }
        }
    }

    override suspend fun deleteAll() {
        dao.deleteAllBody()
        photos.deleteAll()
        seeded = false
    }

    override suspend fun sweepPhotosOnce() {
        if (!swept.compareAndSet(false, true)) return
        photos.sweep(dao.photoFileNames().toSet())
    }
}

private fun MeasurementTypeEntity.toDomain() = MeasurementType(
    id = id,
    builtIn = builtIn?.let(BuiltInMeasurement::byId),
    customName = customName,
    kind = MeasurementKind.valueOf(kind),
    unitLabel = unitLabel,
    tracked = tracked,
    sortOrder = sortOrder,
)

private fun MeasurementType.toEntity(createdAt: Long, updatedAt: Long) = MeasurementTypeEntity(
    id = id,
    builtIn = builtIn?.id,
    customName = customName,
    kind = kind.name,
    unitLabel = unitLabel,
    tracked = tracked,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun CheckInWithDetails.toDomain() = CheckIn(
    id = checkIn.id,
    date = LocalDate.ofEpochDay(checkIn.epochDay),
    measurements = measurements.associate { it.typeId to it.value },
    photos = photos.associate { Pose.valueOf(it.pose) to it.fileName },
)
