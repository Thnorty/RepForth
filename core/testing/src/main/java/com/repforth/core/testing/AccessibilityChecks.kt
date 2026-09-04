package com.repforth.core.testing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import kotlin.math.round

/**
 * What TalkBack would find on a screen, asserted on the JVM.
 *
 * **Why this is a test and not a lint rule.** Android lint's accessibility
 * checks — `ContentDescription`, `TouchTargetSizeCheck`, `ClickableViewAccessibility` —
 * read XML layouts and `View` subclasses. This app has neither. Running
 * `./gradlew lint` over the whole repo reports **zero** accessibility issues,
 * not because there are none but because lint cannot see a Composable. That is
 * a guard that appears to be running and never was, which is the failure shape
 * `AGENTS.md` warns about, so the check had to be built somewhere it can
 * actually look: the merged semantics tree, which is the same thing the
 * accessibility service reads at runtime.
 *
 * It runs through Robolectric beside the screenshot goldens and for the same
 * reason — the matrix that matters is English against Turkish, and no device
 * needs to be plugged in.
 *
 * The merged tree, not the unmerged one, is deliberate: a `Card` that merges
 * its children advertises their text as its own label, and that is precisely
 * what a screen reader announces. Asserting against the unmerged tree would
 * fail every correctly-built composite.
 */

/**
 * Android's stated minimum, from the Material accessibility guidance. Compose's
 * own `MinimumInteractiveComponentSize` expands Material components to this;
 * anything built from a bare `Modifier.clickable` gets no such help, which is
 * exactly the case worth catching.
 */
const val MIN_TOUCH_TARGET_DP = 48

/**
 * Fails with every violation on the screen at once, rather than the first.
 *
 * A one-at-a-time assertion turns a screen with six unlabelled icons into six
 * edit-run cycles, and the whole point of rendering the tree is that it can be
 * read in full.
 */
fun SemanticsNodeInteractionsProvider.assertScreenIsAccessible(screen: String) {
    val problems = mutableListOf<String>()

    val clickable = onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
        useUnmergedTree = false,
    ).fetchSemanticsNodes(atLeastOneRootRequired = false)

    for (node in clickable) {
        if (node.accessibleLabel().isBlank()) {
            problems += "${node.describe()} is clickable but announces nothing: " +
                "no contentDescription, no text, and no onClickLabel."
        }

        val (widthDp, heightDp) = node.touchTargetDp()
        if (widthDp < MIN_TOUCH_TARGET_DP || heightDp < MIN_TOUCH_TARGET_DP) {
            problems += "${node.describe()} has a ${widthDp}x${heightDp}dp touch target, " +
                "under the ${MIN_TOUCH_TARGET_DP}dp minimum."
        }
    }

    // A control that carries state has to say what kind of control it is, or
    // the state is announced without anything to attach it to -- "on", with no
    // "switch" in front of it.
    val stateful = onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)
            .or(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)),
        useUnmergedTree = false,
    ).fetchSemanticsNodes(atLeastOneRootRequired = false)

    for (node in stateful) {
        if (node.config.getOrNull(SemanticsProperties.Role) == null &&
            node.config.getOrNull(SemanticsProperties.StateDescription) == null
        ) {
            problems += "${node.describe()} carries state but declares neither a Role " +
                "nor a stateDescription, so its state is announced with no control to attach it to."
        }
    }

    // A slider is not clickable, so nothing above looks at one. Compose gives it
    // `ProgressBarRangeInfo`, which TalkBack reads as a percentage — so an
    // unnamed slider announces "fifty percent" and leaves the listener to guess
    // what is half full. The AI provider screen shipped exactly that: the
    // timeout slider had "60 seconds" drawn above it as a sibling `Text`, which
    // is not the slider's name and is not announced with it.
    //
    // Deliberately not the touch-target rule. Material measures a slider's
    // handle at 44dp and imposes it from inside the component, where neither
    // `heightIn`, `height` nor `requiredHeight` on the caller's modifier moves
    // it — all three were tried. It is also the wrong rule: 48dp is about
    // hitting a target, and a slider is dragged along a length that here runs
    // the full width of the screen.
    val adjustable = onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress),
        useUnmergedTree = false,
    ).fetchSemanticsNodes(atLeastOneRootRequired = false)

    for (node in adjustable) {
        if (node.accessibleLabel().isBlank()) {
            problems += "${node.describe()} can be adjusted but announces nothing, so its " +
                "value is read out with no name attached. A neighbouring label is not one."
        }
    }

    if (problems.isNotEmpty()) {
        throw AssertionError(
            buildString {
                append("$screen fails ${problems.size} accessibility check(s):\n")
                problems.forEach { append("  - ").append(it).append('\n') }
            },
        )
    }
}

/** Whatever a screen reader would read out for this node, in the order it looks. */
private fun SemanticsNode.accessibleLabel(): String {
    val description = config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ")
        .orEmpty()
    val text = config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(" ") { it.text }
        .orEmpty()
    val clickLabel = config.getOrNull(SemanticsActions.OnClick)?.label.orEmpty()
    return listOf(description, text, clickLabel).joinToString(" ").trim()
}

/**
 * The area a finger actually has to hit, not the area that was drawn.
 *
 * The larger of two measurements, because each one is wrong on its own:
 *
 * - `touchBoundsInRoot` alone is **clipped by an enclosing scroll viewport**.
 *   The first version of this check read only that, and reported the Settings
 *   "keep the screen on" row as a 40dp target in both languages. The row is
 *   built with `heightIn(min = Target.min)`, which is 48dp, so it cannot be
 *   40dp — it was simply the last row at the bottom edge of the `LazyColumn`,
 *   measured as tall as the part of it that had somewhere to be drawn. A guard
 *   that fails on where a row happens to sit is worse than no guard.
 * - `size` alone misses the other direction: a Material `IconButton` draws a
 *   24dp icon and accepts touches across 48dp, so the drawn size reports a
 *   violation that is not there.
 *
 * Taking the larger keeps both properties: clipping can only shrink the touch
 * bounds, and Material's expansion can only grow them past the layout size.
 */
private fun SemanticsNode.touchTargetDp(): Pair<Int, Int> {
    val density = layoutInfo.density.density
    val touch = touchBoundsInRoot
    val width = maxOf(touch.width, size.width.toFloat())
    val height = maxOf(touch.height, size.height.toFloat())
    return round(width / density).toInt() to round(height / density).toInt()
}

/** Enough to find the node in the source without dumping the whole tree. */
private fun SemanticsNode.describe(): String {
    val tag = config.getOrNull(SemanticsProperties.TestTag)
    val label = accessibleLabel().take(40)
    return when {
        !tag.isNullOrBlank() -> "node[$tag]"
        label.isNotBlank() -> "node(\"$label\")"
        else -> "node at ${positionInRoot}"
    }
}
