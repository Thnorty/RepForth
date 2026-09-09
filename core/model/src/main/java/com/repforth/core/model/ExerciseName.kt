package com.repforth.core.model

/**
 * An exercise name as it should be read, from the name as it is stored.
 *
 * The upstream dataset is entirely lower case — "barbell decline wide-grip
 * press" — which is fine as data and looks like a mistake in a heading. All
 * 1,324 records are like that, so this is a display decision applied once rather
 * than a correction to any of them.
 *
 * Applied where a row becomes a domain object, which is the one place all three
 * readers pass through: the catalog, the planner's candidates and the session's
 * summaries. The stored value is untouched, so search still matches what the
 * database actually holds.
 *
 * ### Two details that are not obvious
 *
 * **[Char.uppercaseChar] and not `String.uppercase()`.** The string form is
 * locale-sensitive, and in Turkish — which this app ships — "i" upper-cases to
 * "İ". The names are English, so "incline" would become "İncline" for half the
 * app's users. The character form is locale-invariant and has no such trap.
 *
 * **Hyphens and brackets start words too.** The dataset has 163 hyphenated
 * names and 143 parenthesised qualifiers, so "wide-grip" and "(male)" would
 * otherwise come out half-capitalised. There are no apostrophes in the data,
 * which is what makes this rule safe: `'` as a word break would give "Farmer'S".
 */
fun exerciseDisplayName(stored: String): String = buildString(stored.length) {
    var atWordStart = true
    for (character in stored) {
        append(if (atWordStart) character.uppercaseChar() else character)
        atWordStart = character.isWhitespace() || character in WORD_BREAKS
    }
}

private const val WORD_BREAKS = "-/("
