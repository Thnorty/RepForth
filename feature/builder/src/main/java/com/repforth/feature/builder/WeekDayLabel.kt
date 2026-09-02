package com.repforth.feature.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * "Day 3 · Chest and triceps" — the one place that knows how to name a day.
 *
 * Two screens render this and both got it wrong in the same way, differently.
 * `PlansScreen` joined with `": "` and `WeekReviewScreen` with `" · "`, and
 * neither noticed that the title it was appending very often *already began
 * with the day number*, producing "Day 1: Day 1: Chest".
 *
 * The title arrives carrying that prefix by two separate routes:
 *
 * - **The model writes one.** Nothing told it not to, and "Day 1: Push" is the
 *   natural thing to put in a field called `title`. The prompt now says the app
 *   supplies the number, which is the real fix; this is what catches a model
 *   that does it anyway.
 * - **The app's own fallback is the same string.** When a day comes back with a
 *   blank title, `coach_day_default_title` ("Day %1$d") fills it — and that has
 *   always been character-for-character what `week_day_header` renders beside
 *   it. `WeekDay.title` may not be blank, so the fallback has to say something;
 *   this makes saying the same thing twice harmless.
 */
@Composable
internal fun weekDayLabel(position: Int, title: String): String =
    weekDayLabel(position + 1, stringResource(R.string.week_day_header, position + 1), title)

/**
 * The pure half, so the behaviour above can be tested without a device.
 *
 * [header] is the already-resolved "Day 3" / "3. Gün", and [dayNumber] is the
 * number inside it.
 */
internal fun weekDayLabel(dayNumber: Int, header: String, title: String): String {
    val trimmed = title.trim()
    val body = trimmed.withoutDayPrefix(dayNumber, header)
    return if (body.isEmpty()) header else "$header · $body"
}

/**
 * The title with its own leading day number removed, if it had one.
 *
 * Two passes, because a stored title and the header beside it are not always in
 * the same language. The title is written by the model at generation time and
 * kept; the header is rendered in whatever language the app is in *now*. Switch
 * to Turkish after generating in English and an exact match finds nothing, so
 * "1. Gün · Day 1: Chest" is what the screen showed — found by
 * `BuilderScreenshotTest` on its first run, having survived a unit test that
 * only ever compared a title to its own language's header.
 */
private fun String.withoutDayPrefix(dayNumber: Int, header: String): String {
    if (startsWithHeader(header)) {
        return drop(header.length).trimStart(*TITLE_SEPARATORS)
    }
    val match = DAY_PREFIX.find(this) ?: return this
    // Only this day's own number. "Day 10 recap" is not day one, and neither is
    // a title that happens to mention another day.
    val found = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull()
    return if (found == dayNumber) drop(match.value.length).trimStart(*TITLE_SEPARATORS) else this
}

/**
 * Whether [header] is this title's own leading day number, rather than the start
 * of a longer word.
 *
 * The separator check is the whole point: "Day 10 recap" starts with "Day 1"
 * and is not day one. Watched failing on exactly that.
 */
private fun String.startsWithHeader(header: String): Boolean =
    startsWith(header, ignoreCase = true) &&
        (length == header.length || this[header.length] in TITLE_SEPARATORS)

/**
 * A leading day number in either language this app ships.
 *
 * Mirrors `week_day_header` in `values` and `values-tr`, which is a duplicate
 * and is why `WeekDayLabelTest` checks both forms. It stays a pattern rather
 * than the two resolved strings because the header can only be resolved in the
 * *current* locale, and the whole point here is the title that is in the other
 * one.
 */
private val DAY_PREFIX = Regex(
    """^\s*(?:day\s*(\d+)|(\d+)\s*\.?\s*gün)""",
    RegexOption.IGNORE_CASE,
)

/**
 * What a model puts between "Day 1" and the focus.
 *
 * A period is in here for the Turkish header's own sake: `1. Gün` ends the
 * header, but a model writing `1. Gün. Göğüs` leaves one behind.
 */
private val TITLE_SEPARATORS = charArrayOf(
    ':', '-', '–', '—', '·', '.', ',', ' ', '\t',
)
