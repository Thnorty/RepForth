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

/**
 * What a weight field currently holds.
 *
 * Three states, not two, and the third is the point. A field that cannot be read
 * as a number is not the same as an empty one: blank means "as prescribed" and
 * records the planned weight, so folding an unreadable entry into it records a
 * number the user did not type. That is what used to happen, silently, to
 * anything `toDoubleOrNull` refused.
 */
sealed interface WeightEntry {

    /** Nothing typed. What blank means is the caller's decision. */
    data object Blank : WeightEntry

    /** A weight, in kilograms, whatever unit it was typed in. */
    data class Value(val kg: Double) : WeightEntry

    /** Typed, but not a number. Never a weight, and never a blank. */
    data object Invalid : WeightEntry
}

/**
 * Both decimal separators, always, whichever locale is running.
 *
 * §13 makes Turkish first-class, and a Turkish keyboard's decimal key is a
 * comma. The old filter kept digits and `.` and dropped everything else, so
 * `12,5` did not fail — it became `125`, a tenfold error typed correctly and
 * recorded wrongly, on the one screen where nobody is looking at the field.
 *
 * Accepting both rather than asking the locale is deliberate. What matters is
 * the key the person actually pressed, and phones mix keyboard locale, system
 * locale and app language freely; a hardware keyboard mixes them further. There
 * is no reading of `12,5` or `12.5` other than twelve and a half.
 */
private const val SEPARATORS = ".,"

/** Digits a weight field keeps. Five is 99999, well past any plate loaded. */
private const val MAX_WEIGHT_DIGITS = 5

/**
 * Keeps a weight field to something that could become a weight.
 *
 * Called on every keystroke, so it takes what is already typed and returns what
 * is allowed to stay: digits, capped, and **one** separator — the one the user
 * pressed, left as they pressed it, because a field that rewrites the character
 * under the cursor is a field that fights typing.
 *
 * A second separator is dropped rather than accepted, so `1.2.3` never forms and
 * the parse below has almost nothing left to reject.
 */
fun sanitizeWeightInput(raw: String): String {
    val kept = StringBuilder(raw.length)
    var digits = 0
    var separator = false
    for (c in raw) {
        when {
            c.isDigit() && digits < MAX_WEIGHT_DIGITS -> {
                kept.append(c)
                digits++
            }
            c in SEPARATORS && !separator -> {
                kept.append(c)
                separator = true
            }
        }
    }
    return kept.toString()
}

/**
 * Reads a weight field, in the unit the user types, as kilograms (§7).
 *
 * The conversion belongs here rather than at the call site for the reason §7
 * gives: the stored number must not depend on which unit was selected when it
 * was typed, and that only holds if every field converts in the same place.
 */
fun UnitSystem.readWeight(text: String): WeightEntry {
    if (text.isBlank()) return WeightEntry.Blank
    val value = text.replace(',', '.').toDoubleOrNull() ?: return WeightEntry.Invalid
    // A separator alone parses nowhere, but a negative or non-finite number
    // could arrive from a paste, and neither is a weight.
    if (!value.isFinite() || value < 0.0) return WeightEntry.Invalid
    return WeightEntry.Value(toKilograms(value))
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
