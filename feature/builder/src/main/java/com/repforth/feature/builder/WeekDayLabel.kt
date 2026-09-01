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
 *
 * Case-insensitive because Turkish uses `1. Gün` as the header and `1. gün` as
 * the fallback title, which are the same words and were still not equal.
 */
@Composable
internal fun weekDayLabel(position: Int, title: String): String =
    weekDayLabel(stringResource(R.string.week_day_header, position + 1), title)

/**
 * The pure half, so the behaviour above can be tested without a device.
 *
 * [header] is the already-resolved "Day 3" / "3. Gün".
 */
internal fun weekDayLabel(header: String, title: String): String {
    val trimmed = title.trim()
    val body = if (trimmed.startsWithDayNumber(header)) {
        trimmed.drop(header.length).trimStart(*TITLE_SEPARATORS)
    } else {
        trimmed
    }
    return if (body.isEmpty()) header else "$header · $body"
}

/**
 * Whether [header] is this title's own leading day number, rather than the start
 * of a longer word.
 *
 * The separator check is the whole point: "Day 10 recap" starts with "Day 1"
 * and is not day one. Watched failing on exactly that.
 */
private fun String.startsWithDayNumber(header: String): Boolean =
    startsWith(header, ignoreCase = true) &&
        (length == header.length || this[header.length] in TITLE_SEPARATORS)

/**
 * What a model puts between "Day 1" and the focus.
 *
 * A period is in here for the Turkish header's own sake: `1. Gün` ends the
 * header, but a model writing `1. Gün. Göğüs` leaves one behind.
 */
private val TITLE_SEPARATORS = charArrayOf(
    ':', '-', '–', '—', '·', '.', ',', ' ', '\t',
)
