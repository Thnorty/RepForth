package com.repforth.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.repforth.core.designsystem.R

/**
 * The one place an icon is chosen.
 *
 * Screens name the *role* — [Exercises], not a dumbbell — so the set can be
 * swapped by editing this file rather than by finding every call site. That
 * indirection has now earned itself once: these were two hand-authored
 * drawables and a handful of stock Compose glyphs, and replacing them with the
 * real set changed nothing outside this file.
 *
 * Material Symbols Rounded, Apache 2.0, fetched and converted by
 * `tools/fetch-icons.sh` from the names in `tools/icons.txt`. The design system
 * specifies that set (readme, ICONOGRAPHY) and forbids hand-rolled SVG icons;
 * the only SVG left in the project is the body map, which is data drawing
 * rather than iconography.
 *
 * Everything is a [Painter] so callers never branch on where an icon came from.
 */
object RfIcons {

    // Navigation.
    val Today: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_today)

    val Plans: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_list_alt)

    val Exercises: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_fitness_center)

    val Progress: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_insights)

    val Settings: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_settings)

    /**
     * Back.
     *
     * Not auto-mirrored any more: these are resources rather than
     * `Icons.AutoMirrored`, so RTL mirroring is `android:autoMirrored` on the
     * drawable. Material Symbols already ship arrow_back with that set, and it
     * is preserved by the converter because it copies the path, not the
     * attributes — recorded here because the day RepForth adds an RTL language
     * is the day it matters.
     */
    val Back: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_arrow_back)

    // Editing.
    val Add: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_add)

    val Remove: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_remove)

    val Edit: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_edit)

    val Delete: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_delete)

    val Save: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_save)

    /** Clears a field. Distinct from [Delete], which removes a thing. */
    val Close: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_close)

    val Search: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_search)

    val Filters: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_tune)

    val Expand: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_expand_more)

    val More: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_more_vert)

    val Reorder: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_drag_handle)

    /**
     * Reordering, vertical rather than mirrored.
     *
     * A list moves up and down in every locale; a back/forward pair would
     * mirror in RTL and point somewhere the row does not go.
     */
    val MoveUp: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_expand_less)

    val MoveDown: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_expand_more)

    // The workout.
    val Start: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_play_arrow)

    val SkipNext: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_skip_next)

    val Rest: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_timer)

    val Done: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_check)

    val Completed: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_check_circle)

    // Settings and state.
    val Theme: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_dark_mode)

    val Language: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_translate)

    val Units: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_straighten)

    val Error: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_error)

    val Watch: Painter
        @Composable get() = painterResource(R.drawable.rf_sym_watch)
}
