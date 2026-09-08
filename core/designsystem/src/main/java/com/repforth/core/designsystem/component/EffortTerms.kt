package com.repforth.core.designsystem.component

import androidx.annotation.StringRes
import com.repforth.core.designsystem.R

/**
 * §3's perceived effort as words, from the 1-5 that gets stored.
 *
 * The number is what the database holds and what a history can compare; the
 * sentence is what a person answers. Keeping the mapping in one place is what
 * stops the session screen and the history row disagreeing about what a 4 was —
 * which would not fail anywhere, it would just quietly mean two things.
 *
 * Out of range answers to the middle rather than throwing. Nothing in the app
 * should be able to write one, and if something ever does, a workout row that
 * reads "Just right" is a better failure than a crash on the Progress tab.
 */
@StringRes
fun effortLabel(value: Int): Int = when (value) {
    1 -> R.string.rf_effort_1
    2 -> R.string.rf_effort_2
    4 -> R.string.rf_effort_4
    5 -> R.string.rf_effort_5
    else -> R.string.rf_effort_3
}

/** The scale itself, so callers do not each write `1..5`. */
val EFFORT_SCALE: List<Int> = (1..5).toList()
