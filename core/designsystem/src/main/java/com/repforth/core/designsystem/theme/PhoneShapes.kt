package com.repforth.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * The phone's Material 3 shape scale.
 *
 * The dp values it is cut from -- `Space`, `Layout`, `Target`, `Stroke` and
 * `Radius` -- moved to `core:designtokens` when the watch needed them.
 * `Shapes` is a phone Material 3 type and could not follow.
 */

/** Compact and rounded: 12 cards, 16 large cards, 28 sheets and dialogs. */
val RepForthShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

