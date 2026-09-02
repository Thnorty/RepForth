package com.repforth.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.repforth.core.designsystem.R

/**
 * The parts of a screen only a screen reader ever sees.
 *
 * These live here for the usual reason — two accordions wanted the same three
 * lines, and the second copy is the bug. They matter more than most shared
 * wording because nothing else in this repo can catch them going wrong: a
 * missing `stateDescription` is invisible in a screenshot, silent in a unit
 * test, and lint cannot read a Composable at all.
 */

/**
 * A header row that opens and closes the section under it.
 *
 * Without this a collapsed section reads as bare text with a click action:
 * TalkBack says the week's name and stops, giving no way to know there is
 * anything underneath or whether it is already open.
 */
@Composable
fun Modifier.expandableHeader(expanded: Boolean): Modifier {
    val state = stringResource(if (expanded) R.string.rf_expanded else R.string.rf_collapsed)
    return semantics {
        role = Role.Button
        stateDescription = state
    }
}

/**
 * What the chevron beside such a header is called.
 *
 * Named for what tapping it does rather than which way it points, which is
 * what a screen reader user needs and what the glyph already tells everyone
 * else.
 */
@Composable
fun expandCollapseLabel(expanded: Boolean): String =
    stringResource(if (expanded) R.string.rf_collapse else R.string.rf_expand)
