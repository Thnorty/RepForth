package com.repforth.core.designsystem.theme

import androidx.compose.runtime.compositionLocalOf
import com.repforth.core.model.UnitSystem
import kotlin.math.roundToInt

/**
 * The unit system every weight on screen is drawn in.
 *
 * A composition local rather than a field on each ViewModel: this is a display
 * concern that touches the builder, the running workout and the history, and
 * threading it through three unrelated state objects would put a formatting
 * decision inside three domain layers.
 *
 * §7 is explicit that weights are *stored* in kilograms and converted only for
 * display, so nothing here ever writes back — [toKilograms] exists for the one
 * direction that matters: a number the user typed in pounds is still stored as
 * kilograms.
 *
 * No default worth having, so the default throws in debug and falls back to
 * metric otherwise: a silently metric app for someone who chose pounds is the
 * bug this type exists to prevent.
 */
val LocalUnitSystem = compositionLocalOf { UnitSystem.METRIC }

private const val POUNDS_PER_KILOGRAM = 2.20462262

/** A stored weight, in the unit the user reads. */
fun UnitSystem.fromKilograms(kg: Double): Double = when (this) {
    UnitSystem.METRIC -> kg
    UnitSystem.IMPERIAL -> kg * POUNDS_PER_KILOGRAM
}

/**
 * A weight the user typed, back in kilograms for storage.
 *
 * The round trip is not exact — 100 lb becomes 45.359237 kg and comes back as
 * 100.00000000000001 lb — which is why display rounds and storage does not. A
 * stored value rounded to the display's precision would drift a little every
 * time a plan was opened and saved.
 */
fun UnitSystem.toKilograms(value: Double): Double = when (this) {
    UnitSystem.METRIC -> value
    UnitSystem.IMPERIAL -> value / POUNDS_PER_KILOGRAM
}

/**
 * A weight as text, without the unit.
 *
 * Whole numbers lose the decimal: nobody writes their bench as 60.0, and a
 * trailing zero in a field the user is about to edit is one more character to
 * delete.
 */
fun UnitSystem.formatWeight(kg: Double): String {
    val shown = fromKilograms(kg)
    val rounded = (shown * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** The abbreviation, for labels and for anything that prints a total. */
val UnitSystem.symbol: String
    get() = when (this) {
        UnitSystem.METRIC -> "kg"
        UnitSystem.IMPERIAL -> "lb"
    }

/**
 * A large total, in the unit the user reads.
 *
 * Volume runs to six figures over a season, and "128,450 kg" is not a number
 * anyone parses at a glance — so it becomes tonnes, or short tons in imperial,
 * once it stops being readable.
 */
fun UnitSystem.formatVolume(kg: Double): Pair<String, String> {
    val shown = fromKilograms(kg)
    val threshold = when (this) {
        UnitSystem.METRIC -> 10_000.0
        UnitSystem.IMPERIAL -> 20_000.0
    }
    return if (shown >= threshold) {
        val large = when (this) {
            UnitSystem.METRIC -> shown / 1_000.0
            UnitSystem.IMPERIAL -> shown / 2_000.0
        }
        ((large * 10).roundToInt() / 10.0).toString() to largeSymbol
    } else {
        shown.roundToInt().toString() to symbol
    }
}

private val UnitSystem.largeSymbol: String
    get() = when (this) {
        UnitSystem.METRIC -> "t"
        UnitSystem.IMPERIAL -> "tn"
    }
