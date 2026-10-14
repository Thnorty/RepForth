package com.repforth.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a check-in must hold before it is stored. */
class CheckInRulesTest {

    private val today = LocalDate.of(2026, 10, 13)

    private val types = BuiltInMeasurement.entries.mapIndexed { index, builtIn ->
        MeasurementType(
            id = builtIn.id,
            builtIn = builtIn,
            customName = null,
            kind = builtIn.kind,
            unitLabel = null,
            tracked = builtIn.trackedByDefault,
            sortOrder = index,
        )
    }.associateBy { it.id }

    private fun checkIn(
        measurements: Map<String, Double> = emptyMap(),
        photos: Map<Pose, String> = emptyMap(),
        date: LocalDate = today,
    ) = CheckIn(id = "c", date = date, measurements = measurements, photos = photos)

    private fun check(checkIn: CheckIn) = CheckInRules.check(checkIn, today, types)

    @Test
    fun `an empty check-in is refused`() {
        assertEquals(CheckInProblem.EMPTY, check(checkIn()))
    }

    @Test
    fun `a photo alone is enough`() {
        assertNull(check(checkIn(photos = mapOf(Pose.FRONT to "a.jpg"))))
    }

    @Test
    fun `a future date is refused`() {
        assertEquals(
            CheckInProblem.FUTURE_DATE,
            check(checkIn(measurements = mapOf("weight" to 80.0), date = today.plusDays(1))),
        )
    }

    @Test
    fun `today is allowed`() {
        assertNull(check(checkIn(measurements = mapOf("weight" to 80.0))))
    }

    @Test
    fun `zero or negative is refused`() {
        assertEquals(CheckInProblem.NOT_POSITIVE, check(checkIn(measurements = mapOf("waist" to 0.0))))
        assertEquals(CheckInProblem.NOT_POSITIVE, check(checkIn(measurements = mapOf("waist" to -1.0))))
    }

    @Test
    fun `infinite or NaN is refused`() {
        assertEquals(
            CheckInProblem.NOT_POSITIVE,
            check(checkIn(measurements = mapOf("waist" to Double.POSITIVE_INFINITY))),
        )
        assertEquals(CheckInProblem.NOT_POSITIVE, check(checkIn(measurements = mapOf("waist" to Double.NaN))))
    }

    @Test
    fun `body fat over 100 is refused`() {
        assertEquals(
            CheckInProblem.PERCENT_OVER_100,
            check(checkIn(measurements = mapOf("body_fat" to 100.1))),
        )
        assertNull(check(checkIn(measurements = mapOf("body_fat" to 100.0))))
    }

    @Test
    fun `a measurement of an unknown type is refused`() {
        assertEquals(
            CheckInProblem.UNKNOWN_TYPE,
            check(checkIn(measurements = mapOf("not-a-type" to 10.0))),
        )
    }

    /**
     * The ids are stored and exported, so renaming one orphans every entry
     * already recorded under it.
     */
    @Test
    fun `built-in ids are unique and stable`() {
        assertEquals(
            listOf(
                "weight", "body_fat", "neck", "shoulders", "chest", "waist", "hips",
                "arm_left", "arm_right", "forearm_left", "forearm_right",
                "thigh_left", "thigh_right", "calf_left", "calf_right",
            ),
            BuiltInMeasurement.entries.map { it.id },
        )
        assertEquals(
            setOf(BuiltInMeasurement.WEIGHT, BuiltInMeasurement.BODY_FAT, BuiltInMeasurement.WAIST),
            BuiltInMeasurement.entries.filter { it.trackedByDefault }.toSet(),
        )
        assertEquals(BuiltInMeasurement.WAIST, BuiltInMeasurement.byId("waist"))
        assertNull(BuiltInMeasurement.byId("nope"))
    }
}
