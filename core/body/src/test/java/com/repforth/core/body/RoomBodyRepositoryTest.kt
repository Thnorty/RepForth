package com.repforth.core.body

import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.model.BuiltInMeasurement
import com.repforth.core.model.CheckIn
import com.repforth.core.model.CheckInProblem
import com.repforth.core.model.MeasurementKind
import com.repforth.core.model.Pose
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomBodyRepositoryTest {

    private val dao = FakeBodyDao()
    private val photos = RecordingPhotoStore()

    /** 2026-10-13T12:00Z. */
    private val time = FakeTimeSource(wallClock = 1_791_892_800_000L)
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 13)

    private fun repository() = RoomBodyRepository(dao, photos, time, zone)

    private fun checkIn(
        id: String,
        date: LocalDate = today,
        measurements: Map<String, Double> = emptyMap(),
        photos: Map<Pose, String> = emptyMap(),
    ) = CheckIn(id, date, measurements, photos)

    @Test
    fun `built-ins are seeded once, in order`() = runTest {
        val types = repository().observeTypes().first()

        assertEquals(BuiltInMeasurement.entries.map { it.id }, types.map { it.id })
        assertEquals(
            setOf("weight", "body_fat", "waist"),
            types.filter { it.tracked }.map { it.id }.toSet(),
        )
        assertEquals(BuiltInMeasurement.WAIST, types.single { it.id == "waist" }.builtIn)
    }

    @Test
    fun `seeding does not undo a user's choice`() = runTest {
        val first = repository()
        first.observeTypes().first()
        first.setTracked("neck", true)

        val types = repository().observeTypes().first()

        assertTrue(types.single { it.id == "neck" }.tracked)
    }

    @Test
    fun `an empty check-in is refused`() = runTest {
        assertEquals(SaveCheckIn.Refused(CheckInProblem.EMPTY), repository().save(checkIn("a")))
    }

    @Test
    fun `saving onto a taken day is refused`() = runTest {
        val repository = repository()
        repository.save(checkIn("a", measurements = mapOf("waist" to 86.0)))

        val result = repository.save(checkIn("b", measurements = mapOf("waist" to 80.0)))

        assertEquals(SaveCheckIn.Refused(CheckInProblem.DATE_TAKEN), result)
        assertEquals(86.0, repository.find("a")!!.measurements.getValue("waist"), 0.0)
        assertNull(repository.find("b"))
    }

    @Test
    fun `editing a check-in keeps its day`() = runTest {
        val repository = repository()
        repository.save(checkIn("a", measurements = mapOf("waist" to 86.0)))

        val result = repository.save(checkIn("a", measurements = mapOf("waist" to 85.0)))

        assertEquals(SaveCheckIn.Saved("a"), result)
        assertEquals(85.0, repository.findOn(today)!!.measurements.getValue("waist"), 0.0)
    }

    @Test
    fun `deleting a check-in deletes its photo files`() = runTest {
        val repository = repository()
        repository.save(checkIn("a", photos = mapOf(Pose.FRONT to "f.jpg", Pose.BACK to "b.jpg")))

        repository.delete("a")

        assertNull(repository.find("a"))
        assertEquals(setOf("f.jpg", "b.jpg"), photos.deleted.toSet())
    }

    @Test
    fun `dropping a photo on save deletes its file`() = runTest {
        val repository = repository()
        repository.save(checkIn("a", photos = mapOf(Pose.FRONT to "f.jpg", Pose.BACK to "b.jpg")))

        repository.save(checkIn("a", photos = mapOf(Pose.FRONT to "f.jpg")))

        assertEquals(listOf("b.jpg"), photos.deleted)
    }

    @Test
    fun `a built-in cannot be renamed or deleted`() = runTest {
        val repository = repository()
        repository.observeTypes().first()

        repository.rename("waist", "Belly")
        repository.deleteCustom("waist")

        val waist = repository.observeTypes().first().single { it.id == "waist" }
        assertNull(waist.customName)
    }

    @Test
    fun `a custom type comes after the built-ins`() = runTest {
        val repository = repository()

        val skinfold = repository.addCustom("Skinfold", MeasurementKind.PLAIN, "mm")

        val types = repository.observeTypes().first()
        assertEquals(skinfold.id, types.last().id)
        assertEquals("Skinfold", types.last().customName)
        assertEquals("mm", types.last().unitLabel)
        assertTrue(types.last().tracked)
    }

    @Test
    fun `deleting a custom type removes its entries`() = runTest {
        val repository = repository()
        val skinfold = repository.addCustom("Skinfold", MeasurementKind.PLAIN, "mm")
        repository.save(checkIn("a", measurements = mapOf(skinfold.id to 12.0, "waist" to 86.0)))
        assertEquals(1, repository.entryCount(skinfold.id))

        repository.deleteCustom(skinfold.id)

        assertEquals(0, repository.entryCount(skinfold.id))
        assertEquals(setOf("waist"), repository.find("a")!!.measurements.keys)
    }

    /**
     * Photos are never in an export, so a restore that dropped every check-in
     * the file does not mention would delete every photo the person has.
     */
    @Test
    fun `restore replaces measurements and keeps photographed check-ins`() = runTest {
        val repository = repository()
        val types = repository.observeTypes().first()
        repository.save(
            checkIn(
                "x", date = today.minusDays(10),
                measurements = mapOf("waist" to 90.0), photos = mapOf(Pose.FRONT to "x.jpg"),
            ),
        )
        repository.save(checkIn("y", date = today.minusDays(5), measurements = mapOf("weight" to 82.0)))

        repository.restoreMeasurements(
            types,
            listOf(checkIn("z", date = today.minusDays(1), measurements = mapOf("weight" to 81.0))),
        )

        val x = repository.find("x")
        assertNotNull("A photographed check-in survives", x)
        assertEquals(mapOf(Pose.FRONT to "x.jpg"), x!!.photos)
        assertEquals(emptyMap<String, Double>(), x.measurements)
        assertNull("A check-in the file does not hold, with no photo, goes", repository.find("y"))
        assertEquals(81.0, repository.find("z")!!.measurements.getValue("weight"), 0.0)
        assertEquals(emptyList<String>(), photos.deleted)
    }

    /**
     * A check-in whose only value was the deleted type would be left holding
     * nothing -- shown as "0 measurements", and exported as a check-in that
     * every import then refuses, taking the whole backup with it.
     */
    @Test
    fun `deleting a custom type removes check-ins it leaves empty`() = runTest {
        val repository = repository()
        val skinfold = repository.addCustom("Skinfold", MeasurementKind.PLAIN, "mm")
        repository.save(checkIn("only", date = today.minusDays(2), measurements = mapOf(skinfold.id to 12.0)))
        repository.save(
            checkIn(
                "photographed", date = today.minusDays(1),
                measurements = mapOf(skinfold.id to 11.0), photos = mapOf(Pose.FRONT to "f.jpg"),
            ),
        )

        repository.deleteCustom(skinfold.id)

        assertNull(repository.find("only"))
        assertEquals("Photos alone still make a check-in", mapOf(Pose.FRONT to "f.jpg"), repository.find("photographed")!!.photos)
    }

    /**
     * The backup was taken, then the photographed check-in was moved to another
     * day. The file names its id on the old day; writing that over it would
     * empty its photos, and the photos are the one thing no file can restore.
     */
    @Test
    fun `restore keeps photos when the photographed check-in moved day`() = runTest {
        val repository = repository()
        val types = repository.observeTypes().first()
        repository.save(checkIn("p", date = today.minusDays(1), photos = mapOf(Pose.FRONT to "p.jpg")))

        repository.restoreMeasurements(
            types,
            listOf(checkIn("p", date = today.minusDays(2), measurements = mapOf("waist" to 88.0))),
        )

        assertEquals(mapOf(Pose.FRONT to "p.jpg"), repository.findOn(today.minusDays(1))!!.photos)
        assertEquals(88.0, repository.findOn(today.minusDays(2))!!.measurements.getValue("waist"), 0.0)
        assertEquals(listOf("p.jpg"), repository.observeCheckIns().first().flatMap { it.photos.values })
    }

    @Test
    fun `restore onto a photographed day merges into it`() = runTest {
        val repository = repository()
        val types = repository.observeTypes().first()
        repository.save(checkIn("x", date = today.minusDays(3), photos = mapOf(Pose.SIDE to "s.jpg")))

        repository.restoreMeasurements(
            types,
            listOf(checkIn("other-id", date = today.minusDays(3), measurements = mapOf("waist" to 84.0))),
        )

        val merged = repository.findOn(today.minusDays(3))!!
        assertEquals(mapOf(Pose.SIDE to "s.jpg"), merged.photos)
        assertEquals(84.0, merged.measurements.getValue("waist"), 0.0)
    }

    @Test
    fun `deleteAll removes every file`() = runTest {
        val repository = repository()
        repository.save(checkIn("a", photos = mapOf(Pose.FRONT to "f.jpg")))

        repository.deleteAll()

        assertEquals(1, photos.deletedAll)
        assertTrue(repository.observeCheckIns().first().isEmpty())
    }

    @Test
    fun `the sweep runs once`() = runTest {
        val repository = repository()
        repository.save(checkIn("a", photos = mapOf(Pose.FRONT to "f.jpg")))

        repository.sweepPhotosOnce()
        repository.sweepPhotosOnce()

        assertEquals(listOf(setOf("f.jpg")), photos.sweeps)
    }
}
