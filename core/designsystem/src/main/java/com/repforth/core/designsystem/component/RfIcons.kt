package com.repforth.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.repforth.core.designsystem.R

/**
 * The one place an icon is chosen.
 *
 * Screens name the *role* — [Exercises], not a dumbbell — so the app can be
 * re-pointed at the imported Material Symbols Rounded set by editing this file,
 * rather than by finding every call site.
 *
 * Everything is a [Painter], including the ones backed by [Icons], so callers
 * never branch on where an icon came from. That matters because the source is
 * expected to change: two of these are hand-authored drawables, since the icon
 * set has not been imported yet and the stock Compose set has nothing meaning
 * "exercise catalog" or "progress". A wrong icon on a primary tab is worse than
 * a simple correct one.
 */
object RfIcons {

    val Today: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.DateRange)

    val Plans: Painter
        @Composable get() = rememberVectorPainter(Icons.AutoMirrored.Rounded.List)

    val Exercises: Painter
        @Composable get() = painterResource(R.drawable.rf_ic_exercises)

    val Progress: Painter
        @Composable get() = painterResource(R.drawable.rf_ic_progress)

    val Settings: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.Settings)

    /** Clears a field. `Clear` rather than `Close`: it empties, it does not dismiss. */
    val Close: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.Clear)

    /** Auto-mirrored: the manifest declares RTL support, so back points the
     *  other way in an RTL locale. Same for [Plans]. */
    val Back: Painter
        @Composable get() = rememberVectorPainter(Icons.AutoMirrored.Rounded.ArrowBack)

    val Add: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.Add)

    /** Removes a row from a plan. Distinct from [Close], which empties a field. */
    val Delete: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.Delete)

    /**
     * Reordering. Vertical rather than auto-mirrored back/forward: a list moves
     * up and down in every locale, and mirroring the arrows in RTL would make
     * them point somewhere the row does not go.
     */
    val MoveUp: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.KeyboardArrowUp)

    val MoveDown: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.KeyboardArrowDown)
}
