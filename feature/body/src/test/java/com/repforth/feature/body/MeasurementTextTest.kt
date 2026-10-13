package com.repforth.feature.body

import com.repforth.core.model.MeasurementKind
import com.repforth.core.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A typed value back into the canonical unit it is stored in. */
class MeasurementTextTest {

    @Test
    fun `imperial length reads back to centimetres`() {
        assertEquals(86.36, readMeasurement("34", MeasurementKind.LENGTH, UnitSystem.IMPERIAL)!!, 1e-9)
    }

    @Test
    fun `imperial weight reads back to kilograms`() {
        assertEquals(45.359237, readMeasurement("100", MeasurementKind.MASS, UnitSystem.IMPERIAL)!!, 1e-6)
    }

    @Test
    fun `a Turkish comma reads as a decimal`() {
        assertEquals(88.5, readMeasurement("88,5", MeasurementKind.LENGTH, UnitSystem.METRIC)!!, 1e-9)
    }

    @Test
    fun `plain and percent are stored as typed`() {
        assertEquals(18.0, readMeasurement("18", MeasurementKind.PLAIN, UnitSystem.IMPERIAL)!!, 0.0)
        assertEquals(21.5, readMeasurement("21.5", MeasurementKind.PERCENT, UnitSystem.IMPERIAL)!!, 0.0)
    }

    @Test
    fun `blank or nonsense reads as nothing`() {
        assertNull(readMeasurement("", MeasurementKind.LENGTH, UnitSystem.METRIC))
        assertNull(readMeasurement("x", MeasurementKind.LENGTH, UnitSystem.METRIC))
    }
}
