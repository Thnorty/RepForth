package com.repforth.core.designsystem.theme

import com.repforth.core.model.UnitSystem
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lengths are stored in centimetres and shown in the user's units (§7). */
class LengthUnitsTest {

    private val turkish = Locale.forLanguageTag("tr-TR")

    @Test
    fun `inches round-trip without drift`() {
        var cm = 50.0
        while (cm <= 150.0) {
            val back = UnitSystem.IMPERIAL.toCentimetres(UnitSystem.IMPERIAL.fromCentimetres(cm))
            assertEquals(cm, back, 1e-9)
            cm += 0.1
        }
    }

    @Test
    fun `metric is identity`() {
        assertEquals(86.25, UnitSystem.METRIC.fromCentimetres(86.25), 0.0)
        assertEquals(86.25, UnitSystem.METRIC.toCentimetres(86.25), 0.0)
    }

    @Test
    fun `formatLength shows one decimal in the locale`() {
        assertEquals("86", UnitSystem.METRIC.formatLength(86.0, Locale.ENGLISH))
        assertEquals("86.3", UnitSystem.METRIC.formatLength(86.25, Locale.ENGLISH))
        assertEquals("86,3", UnitSystem.METRIC.formatLength(86.25, turkish))
        assertEquals("34", UnitSystem.IMPERIAL.formatLength(86.36, Locale.ENGLISH))
    }

    @Test
    fun `length symbols`() {
        assertEquals("cm", UnitSystem.METRIC.lengthSymbol)
        assertEquals("in", UnitSystem.IMPERIAL.lengthSymbol)
    }

    @Test
    fun `readDecimal accepts comma and point`() {
        assertEquals(34.5, readDecimal("34,5")!!, 0.0)
        assertEquals(34.5, readDecimal("34.5")!!, 0.0)
        assertNull(readDecimal(""))
        assertNull(readDecimal("  "))
        assertNull(readDecimal("abc"))
    }
}
