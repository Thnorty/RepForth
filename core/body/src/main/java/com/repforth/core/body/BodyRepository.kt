package com.repforth.core.body

import com.repforth.core.model.CheckIn
import com.repforth.core.model.CheckInProblem
import com.repforth.core.model.MeasurementKind
import com.repforth.core.model.MeasurementType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

sealed interface SaveCheckIn {
    data class Saved(val id: String) : SaveCheckIn
    data class Refused(val problem: CheckInProblem) : SaveCheckIn
}

/**
 * Body check-ins and the types that can be measured.
 *
 * The built-in types are seeded here before the first read, rather than by a
 * migration, because a fresh install starts from the prepackaged database and
 * never migrates. Seeding never overwrites a row, so a choice the user made
 * survives every later start.
 */
interface BodyRepository {

    /** Every type, built-ins first in their fixed order, then custom ones as added. */
    fun observeTypes(): Flow<List<MeasurementType>>

    /** Newest date first. */
    fun observeCheckIns(): Flow<List<CheckIn>>

    suspend fun find(id: String): CheckIn?

    suspend fun findOn(date: LocalDate): CheckIn?

    /**
     * Stores [checkIn], replacing what that id held. Refused by `CheckInRules`,
     * or with [CheckInProblem.DATE_TAKEN] when another check-in has that date.
     * Photo files the save dropped are deleted.
     */
    suspend fun save(checkIn: CheckIn): SaveCheckIn

    /** The check-in, then its photo files. */
    suspend fun delete(id: String)

    suspend fun setTracked(typeId: String, tracked: Boolean)

    suspend fun addCustom(name: String, kind: MeasurementKind, unitLabel: String?): MeasurementType

    /** Custom types only; a built-in takes its name from resources. */
    suspend fun rename(typeId: String, name: String)

    /** Custom types only, and its measurements with it. Built-ins can only be turned off. */
    suspend fun deleteCustom(typeId: String)

    suspend fun entryCount(typeId: String): Int

    /**
     * An import's body section, as a restore of everything an export carries.
     *
     * Types take the file's tracked flags and order, and custom types the file
     * does not hold are deleted. Check-ins become the file's — except that a
     * check-in holding photos is never deleted, because photos are never in a
     * file: it keeps its photos, and its measurements become the file's for that
     * day, or none. Atomicity is the caller's transaction.
     */
    suspend fun restoreMeasurements(types: List<MeasurementType>, checkIns: List<CheckIn>)

    /** Every body row and every photo file ("Reset app"). */
    suspend fun deleteAll()

    /** Removes photo files nothing refers to. Does nothing after its first call in a process. */
    suspend fun sweepPhotosOnce()
}
