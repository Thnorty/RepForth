package com.repforth.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.repforth.core.database.dao.BodyDao
import com.repforth.core.database.entity.CheckInEntity
import com.repforth.core.database.entity.MeasurementEntity
import com.repforth.core.database.entity.MeasurementTypeEntity
import com.repforth.core.database.entity.ProgressPhotoEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The body tables against a real SQLite.
 *
 * The cascades and the one-check-in-per-day rule are constraints SQLite
 * enforces, so a JVM fake could only restate them; this is where they are
 * actually seen to hold.
 */
@RunWith(AndroidJUnit4::class)
class BodyDaoTest {

    private lateinit var database: RepForthDatabase
    private lateinit var dao: BodyDao

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RepForthDatabase::class.java,
        ).build()
        dao = database.bodyDao()
    }

    @After
    fun close() = database.close()

    private fun type(id: String, tracked: Boolean = false) = MeasurementTypeEntity(
        id = id, builtIn = id, customName = null, kind = "LENGTH", unitLabel = null,
        tracked = tracked, sortOrder = 0, createdAt = 1, updatedAt = 1,
    )

    private fun checkIn(id: String, day: Long) = CheckInEntity(id, day, createdAt = 1, updatedAt = 1)

    private fun measurement(checkInId: String, typeId: String, value: Double) =
        MeasurementEntity("$checkInId:$typeId", checkInId, typeId, value, createdAt = 1, updatedAt = 1)

    private fun photo(checkInId: String) =
        ProgressPhotoEntity("$checkInId:FRONT", checkInId, "FRONT", "a.jpg", createdAt = 1, updatedAt = 1)

    @Test
    fun deleting_a_check_in_deletes_its_measurements_and_photos() = runBlocking {
        dao.insertTypesIfMissing(listOf(type("waist")))
        dao.saveCheckIn(checkIn("c1", 100), listOf(measurement("c1", "waist", 86.0)), listOf(photo("c1")))

        dao.deleteCheckIn("c1")

        assertNull(dao.findCheckIn("c1"))
        assertEquals(0, dao.countMeasurements("waist"))
        assertEquals(emptyList<String>(), dao.photoFileNames())
    }

    @Test
    fun deleting_a_type_deletes_its_measurements() = runBlocking {
        dao.insertTypesIfMissing(listOf(type("waist"), type("neck")))
        dao.saveCheckIn(
            checkIn("c1", 100),
            listOf(measurement("c1", "waist", 86.0), measurement("c1", "neck", 38.0)),
            emptyList(),
        )

        dao.deleteType("neck")

        val left = dao.findCheckIn("c1")!!.measurements.map { it.typeId }
        assertEquals(listOf("waist"), left)
    }

    @Test
    fun two_check_ins_cannot_share_a_day() = runBlocking {
        dao.insertCheckIn(checkIn("c1", 100))
        try {
            dao.saveCheckIn(checkIn("c2", 100), emptyList(), emptyList())
            fail("A second check-in on the same day was stored")
        } catch (expected: SQLiteConstraintException) {
            // The unique index on epoch_day refused it.
        }
        assertEquals("c1", dao.findCheckInOnDay(100)!!.checkIn.id)
    }

    @Test
    fun inserting_types_if_missing_keeps_existing_rows() = runBlocking {
        dao.insertTypesIfMissing(listOf(type("neck", tracked = true)))

        dao.insertTypesIfMissing(listOf(type("neck", tracked = false)))

        assertTrue(dao.types().single().tracked)
    }
}
