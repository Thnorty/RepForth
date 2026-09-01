package com.repforth.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Ported from design-system/tokens/spacing.css and tokens/shape.css.
 * Values are dp — the CSS px values were already authored as dp at 1x.
 */

/** 4dp grid. Components use these, never raw numbers. */
object Space {
    val s0 = 0.dp
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
    val s16 = 64.dp
    val s20 = 80.dp
}

/** Layout constants. Phones, not tablets: content is a single column capped at 412dp. */
object Layout {
    val gutterPhone = 16.dp
    val gutterPhoneTight = 12.dp
    val gutterWear = 8.dp
    val contentMaxPhone = 412.dp
    val listRowGap = 8.dp
    val sectionGap = 24.dp

    /** Tall enough for the silhouette to stay tappable inside a scrolling filter panel. */
    val bodyMapHeight = 280.dp

    val appBarHeight = 64.dp
    val navBarHeight = 80.dp
    val fabSize = 56.dp
    val fabSizeLarge = 72.dp

    /**
     * Ceiling for a quoted machine payload inside a dialog.
     *
     * Enough to read an error response without the dialog's buttons being
     * pushed off screen by something the app did not write. The block scrolls
     * past this rather than growing.
     */
    val dialogCodeMaxHeight = 180.dp
}

/**
 * Touch targets. These are an accessibility floor, not a preference.
 *
 * [session] is the important one: anything tapped mid-set is 64dp, because the
 * user is out of breath with chalk on their hands. [icon] (40dp) is permitted
 * only for non-primary controls and never for a primary action.
 */
object Target {
    val min = 48.dp
    val session = 64.dp
    val wear = 52.dp
    val icon = 40.dp
}

/** Stroke widths. */
object Stroke {
    val hairline = 1.dp
    val thick = 2.dp
    val ring = 8.dp
    val ringWear = 6.dp
}

/** Named radii beyond the M3 [Shapes] set. */
object Radius {
    val card = 12.dp
    val cardLarge = 16.dp
    val chip = 999.dp
    val button = 999.dp
    val buttonSquare = 16.dp
    val sheet = 28.dp
    val media = 12.dp
    val full = 999.dp
}

/** Compact and rounded: 12 cards, 16 large cards, 28 sheets and dialogs. */
val RepForthShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
