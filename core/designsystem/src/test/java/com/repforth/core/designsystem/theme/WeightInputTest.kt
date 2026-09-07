package com.repforth.core.designsystem.theme

import com.repforth.core.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one weight parser, which two screens now share.
 *
 * It exists because both of them filtered input by hand and both got the same
 * thing wrong: they kept digits and `.` and dropped everything else, so a comma
 * did not fail — `12,5` became `125`. §13 makes Turkish first-class and a
 * Turkish keyboard's decimal key is a comma, so this was a tenfold error
 * available to half the intended users by typing the number correctly.
 *
 * The second half is that an unreadable entry used to arrive as null, which both
 * screens already used to mean "as prescribed". So the failure mode was not an
 * error message; it was recording the planned weight and saying nothing.
 */
class WeightInputTest {

    // ---- What may stay in the field ----

    @Test
    fun `a comma is a decimal point`() {
        assertEquals("12,5", sanitizeWeightInput("12,5"))
        assertEquals(WeightEntry.Value(12.5), UnitSystem.METRIC.readWeight("12,5"))
    }

    @Test
    fun `a period is a decimal point too`() {
        assertEquals("12.5", sanitizeWeightInput("12.5"))
        assertEquals(WeightEntry.Value(12.5), UnitSystem.METRIC.readWeight("12.5"))
    }

    @Test
    fun `the separator the user typed is the one that stays`() {
        // Rewriting it would mean the character under the cursor changing as
        // they type, which is a field that fights back.
        assertEquals(",", sanitizeWeightInput(","))
        assertEquals(".", sanitizeWeightInput("."))
    }

    @Test
    fun `a second separator never forms`() {
        // `1.2.3` was reachable before, parsed as nothing, and silently logged
        // the target.
        assertEquals("1.23", sanitizeWeightInput("1.2.3"))
        assertEquals("1,23", sanitizeWeightInput("1,2,3"))
        assertEquals("1.23", sanitizeWeightInput("1.2,3"))
    }

    @Test
    fun `letters and signs are not weights`() {
        assertEquals("60", sanitizeWeightInput("60kg"))
        assertEquals("60", sanitizeWeightInput("-60"))
        assertEquals("", sanitizeWeightInput("heavy"))
    }

    @Test
    fun `digits are capped and the separator is not one of them`() {
        // Truncated, the way a length limit on a field behaves — the extra
        // keystrokes simply do not arrive.
        assertEquals("12345", sanitizeWeightInput("123456789"))
        // Five digits either side of the point, not five characters: capping
        // the whole string would spend the limit on the separator and cut a
        // digit off a four-figure weight.
        assertEquals("1234.5", sanitizeWeightInput("1234.56"))
        assertEquals("12.345", sanitizeWeightInput("12.3456"))
    }

    // ---- What the field means ----

    @Test
    fun `blank means as prescribed`() {
        assertEquals(WeightEntry.Blank, UnitSystem.METRIC.readWeight(""))
        assertEquals(WeightEntry.Blank, UnitSystem.METRIC.readWeight("   "))
    }

    @Test
    fun `a separator on its own is invalid and not blank`() {
        // The distinction the old code did not have. Both of these reached
        // `toDoubleOrNull`, came back null, and were recorded as the target.
        assertEquals(WeightEntry.Invalid, UnitSystem.METRIC.readWeight("."))
        assertEquals(WeightEntry.Invalid, UnitSystem.METRIC.readWeight(","))
    }

    @Test
    fun `zero is a weight`() {
        // Bodyweight work is logged at zero, and it must not be mistaken for
        // "nothing typed" — that would record the plan's number instead.
        assertEquals(WeightEntry.Value(0.0), UnitSystem.METRIC.readWeight("0"))
    }

    @Test
    fun `a trailing separator is the number so far`() {
        // Mid-typing, and it has to parse: the builder writes through on every
        // keystroke, so refusing here would drop the weight the moment someone
        // reached for the decimal key.
        assertEquals(WeightEntry.Value(12.0), UnitSystem.METRIC.readWeight("12,"))
        assertEquals(WeightEntry.Value(0.5), UnitSystem.METRIC.readWeight(",5"))
    }

    @Test
    fun `pounds are read as kilograms`() {
        // §7: the stored number never depends on which unit was selected when it
        // was typed, and that only holds if the conversion is in the parser.
        val entry = UnitSystem.IMPERIAL.readWeight("100")
        assertEquals(45.359237, (entry as WeightEntry.Value).kg, 0.0001)
    }

    @Test
    fun `a pounds entry with a comma converts too`() {
        val entry = UnitSystem.IMPERIAL.readWeight("2,2")
        assertEquals(0.9979, (entry as WeightEntry.Value).kg, 0.0001)
    }

    @Test
    fun `what the display writes is what the parser reads back`() {
        // The two halves of the round trip live in this file and have to agree,
        // or a weight loaded into a field would be rewritten on sight.
        listOf(UnitSystem.METRIC, UnitSystem.IMPERIAL).forEach { units ->
            listOf(0.0, 2.5, 20.0, 60.0, 142.5).forEach { kg ->
                val text = units.formatWeight(kg)
                val read = units.readWeight(text)
                assertEquals(
                    "$units round trip of $kg through \"$text\"",
                    kg,
                    (read as WeightEntry.Value).kg,
                    // formatWeight rounds to one decimal in the display unit,
                    // which is a tenth of a pound at worst.
                    0.05,
                )
            }
        }
    }
}
