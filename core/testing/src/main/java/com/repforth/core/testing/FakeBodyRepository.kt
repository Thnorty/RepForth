package com.repforth.core.testing

import com.repforth.core.body.BodyRepository
import com.repforth.core.body.SaveCheckIn
import com.repforth.core.model.BuiltInMeasurement
import com.repforth.core.model.CheckIn
import com.repforth.core.model.CheckInProblem
import com.repforth.core.model.CheckInRules
import com.repforth.core.model.MeasurementKind
import com.repforth.core.model.MeasurementType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Body data in memory, for the modules that consume `BodyRepository`.
 *
 * Holds the same rules as the real one — `CheckInRules`, one check-in per day,
 * photographed check-ins surviving a restore — so a screen test cannot pass on
 * behaviour the app would refuse. [today] stands in for the clock.
 */
class FakeBodyRepository(
    private val today: LocalDate = LocalDate.of(2026, 10, 13),
) : BodyRepository {

    val types = MutableStateFlow(
        BuiltInMeasurement.entries.mapIndexed { index, builtIn ->
            MeasurementType(builtIn.id, builtIn, null, builtIn.kind, null, builtIn.trackedByDefault, index)
        },
    )

    val checkIns = MutableStateFlow<List<CheckIn>>(emptyList())

    /** File names the repository was asked to delete, as the real one asks the photo store. */
    val deletedPhotos = mutableListOf<String>()

    var sweeps = 0
        private set

    private var nextCustom = 0

    override fun observeTypes(): Flow<List<MeasurementType>> = types

    override fun observeCheckIns(): Flow<List<CheckIn>> = checkIns.map { list -> list.sortedByDescending { it.date } }

    override suspend fun find(id: String): CheckIn? = checkIns.value.firstOrNull { it.id == id }

    override suspend fun findOn(date: LocalDate): CheckIn? = checkIns.value.firstOrNull { it.date == date }

    override suspend fun save(checkIn: CheckIn): SaveCheckIn {
        CheckInRules.check(checkIn, today, types.value.associateBy { it.id })?.let { return SaveCheckIn.Refused(it) }
        if (checkIns.value.any { it.date == checkIn.date && it.id != checkIn.id }) {
            return SaveCheckIn.Refused(CheckInProblem.DATE_TAKEN)
        }
        val previous = find(checkIn.id)
        checkIns.value = checkIns.value.filterNot { it.id == checkIn.id } + checkIn
        deletedPhotos += previous?.photos?.values.orEmpty().filterNot { it in checkIn.photos.values }
        return SaveCheckIn.Saved(checkIn.id)
    }

    override suspend fun delete(id: String) {
        val existing = find(id) ?: return
        checkIns.value = checkIns.value - existing
        deletedPhotos += existing.photos.values
    }

    override suspend fun setTracked(typeId: String, tracked: Boolean) {
        types.value = types.value.map { if (it.id == typeId) it.copy(tracked = tracked) else it }
    }

    override suspend fun addCustom(name: String, kind: MeasurementKind, unitLabel: String?): MeasurementType {
        val type = MeasurementType(
            id = "custom-${nextCustom++}",
            builtIn = null,
            customName = name,
            kind = kind,
            unitLabel = unitLabel.takeIf { kind == MeasurementKind.PLAIN },
            tracked = true,
            sortOrder = (types.value.maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        types.value = types.value + type
        return type
    }

    override suspend fun rename(typeId: String, name: String) {
        types.value = types.value.map { if (it.id == typeId && it.builtIn == null) it.copy(customName = name) else it }
    }

    override suspend fun deleteCustom(typeId: String) {
        if (types.value.none { it.id == typeId && it.builtIn == null }) return
        types.value = types.value.filterNot { it.id == typeId }
        // As the real repository: a check-in left holding nothing goes.
        checkIns.value = checkIns.value
            .map { it.copy(measurements = it.measurements - typeId) }
            .filterNot { it.measurements.isEmpty() && it.photos.isEmpty() }
    }

    override suspend fun entryCount(typeId: String): Int = checkIns.value.count { typeId in it.measurements }

    override suspend fun restoreMeasurements(types: List<MeasurementType>, checkIns: List<CheckIn>) {
        val fileIds = types.map { it.id }.toSet()
        this.types.value = this.types.value.filter { it.builtIn != null && it.id !in fileIds } + types
        val photographed = this.checkIns.value.filter { it.photos.isNotEmpty() }
            .map { it.copy(measurements = emptyMap()) }
            .associateBy { it.date }
            .toMutableMap()
        val restored = checkIns.map { incoming ->
            photographed.remove(incoming.date)?.copy(measurements = incoming.measurements)
                ?: incoming.copy(photos = emptyMap())
        }
        this.checkIns.value = restored + photographed.values
    }

    override suspend fun deleteAll() {
        deletedPhotos += checkIns.value.flatMap { it.photos.values }
        checkIns.value = emptyList()
    }

    override suspend fun sweepPhotosOnce() {
        sweeps++
    }
}
