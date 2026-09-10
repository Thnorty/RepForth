package com.repforth.wear

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.repforth.core.designsystem.theme.LocalRepForthColors
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.wearprotocol.WearAction
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState

/**
 * The five screens §11 names, and nothing else.
 *
 * Every one of them is a projection of a snapshot the phone sent. There is no
 * local state to get out of step, which is the whole design: the watch cannot
 * be wrong about the workout, only out of date, and being out of date is what
 * the revision on each command exists to make harmless.
 *
 * **What changed in the design pass, and why.** These were laid out one control
 * at a time as the feature set grew, and arrived as a column of full-width
 * buttons that a wrist had to scroll. The owner asked for a pass on 2026-09-09
 * after wearing it. Three things drive the new shape:
 *
 * - **A glance is not a scroll.** During a set the hand is on a bar. What the
 *   screen owes is one number and one action, and everything else is in the way
 *   of both. Pause, skip and next exercise are between-set decisions and have
 *   moved to a page of their own.
 * - **The rim is the biggest thing a round display has**, and it was empty. It
 *   now carries progress: how far through the workout during a set, and how much
 *   rest is left during a rest. §11 asked for "a large circular countdown" on the
 *   rest screen from the start and there had never been a circle.
 * - **The app has a design system and the watch had never used it.** These ran
 *   on bare `MaterialTheme {}` -- stock Wear lavender, stock fonts -- while §12
 *   asks for charcoal, one lime accent, and numerals as the hero. See
 *   [RepForthWearTheme].
 */

// ----------------------------------------------------------- message screens

/** §11 screen 1: the phone cannot be reached. */
@Composable
fun DisconnectedScreen(lastSeen: WearWorkoutState?, modifier: Modifier = Modifier) {
    Centred(modifier) {
        Text(
            text = stringResource(R.string.wear_disconnected_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.wear_disconnected_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // §11 allows the last snapshot to stay visible while disconnected, and
        // it is worth keeping: someone glancing down mid-set wants to know
        // which set they are on, and that does not stop being true because the
        // phone went out of range. What is gone is every button.
        lastSeen?.let { state ->
            Text(
                text = state.exerciseName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** §11 screen 2: connected, but nothing is running. */
@Composable
fun NoWorkoutScreen(modifier: Modifier = Modifier) {
    Centred(modifier) {
        Text(
            text = stringResource(R.string.wear_no_workout_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.wear_no_workout_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** §11 screen 5: over. */
@Composable
fun FinishedScreen(state: WearWorkoutState, modifier: Modifier = Modifier) {
    Centred(modifier) {
        Text(
            text = stringResource(
                // Abandoned is not finished, and the watch says which. The
                // phone keeps these apart deliberately; congratulating someone
                // for giving up would throw that away at the last step.
                if (state.phase == WearPhase.Abandoned) {
                    R.string.wear_workout_ended
                } else {
                    R.string.wear_workout_finished
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.wear_summary_on_phone),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ------------------------------------------------------------- the set pages

/**
 * §11 screen 3: working a set — counted in repetitions, or in seconds.
 *
 * The two are genuinely different screens sharing a frame, and the difference is
 * not decoration. A timed set **cannot be completed by hand**: the phone's
 * engine refuses `CompleteSet` for a duration target, because §3's timed work is
 * measured rather than declared and a plank stopped at forty seconds is a skip,
 * not a sixty-second set. So the Complete button is absent rather than disabled
 * — a disabled control says "not yet", and this one is never coming.
 *
 * That refusal already existed on the phone when this screen did not know about
 * it, which is the shape worth naming: the watch went on offering a button whose
 * command the phone would reject, and every engine test stayed green because
 * they test the rule, not the sender. §11 makes the watch a second sender, and a
 * second sender has to be looked at whenever the rules change.
 *
 * [remainingSeconds] is what the clock says, or null before the phone has armed
 * one — in which case the prescription is drawn instead, which is the honest
 * answer to "how long is this" when nothing is counting yet.
 *
 * The rim carries **workout** progress here rather than anything about this set.
 * The set's own position is the number in the middle, and repeating it around
 * the edge would spend the display's largest feature saying something already
 * said. What it answers instead is the question the watch could not answer at
 * all: how much of this workout is left.
 */
@Composable
fun ExercisePage(
    state: WearWorkoutState,
    remainingSeconds: Int?,
    modifier: Modifier = Modifier,
    /** Room the pager reserves for its edge button. Zero when there is none. */
    bottomInset: Dp = 0.dp,
) {
    val timed = state.targetDurationMs != null

    ArcFrame(
        progress = state.workoutProgress(),
        colour = MaterialTheme.colorScheme.primary,
        bottomInset = bottomInset,
        modifier = modifier,
    ) {
        Text(
            text = state.exerciseName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // **Two lines, because one is not enough for this catalog.**
            //
            // The first version used one, on the argument that a name is
            // identity rather than instruction. The catalog disagrees: the
            // median name is 26 characters and 69% are over 20, so a single
            // line truncates the normal case rather than the long one.
            //
            // Worse, it truncates from the wrong end. These names lead with
            // equipment and distinguish themselves later, so cutting at 20
            // characters leaves one name in five identical to another --
            // "bodyweight standing..." is seven different exercises. Two lines
            // reach about forty characters, where that falls to two names in
            // 1,318.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Its own inset, on top of the column's. This is the topmost line
            // on the page and therefore the one nearest the curve, where the
            // circle is far narrower than its diameter -- a name set to the
            // same width as the number below it loses its first and last
            // letters to the glass.
            modifier = Modifier.padding(horizontal = NAME_INSET),
        )

        // The clock takes the big slot for timed work, exactly as it does on
        // the phone. During a plank the only number worth that space is how long
        // is left; the prescription is merely what the seconds started at.
        HeroNumber(
            value = if (timed) {
                (remainingSeconds ?: (state.targetDurationMs!! / 1000L).toInt()).toString()
            } else {
                state.targetReps?.toString() ?: "—"
            },
            unit = if (timed) R.string.wear_seconds else R.string.wear_reps_label,
            colour = if (timed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        Text(
            text = stringResource(R.string.wear_set_of, state.setNumber, state.totalSets),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * §11 screen 4: counting down between sets, on the circle §11 always asked for.
 *
 * The ring depletes as the rest runs, so the amount of colour left *is* the
 * amount of rest left and the number in the middle only confirms it. Amber
 * rather than lime: the palette gives rest its own role and this is the screen
 * it was named for.
 *
 * A rest with no [WearWorkoutState.restTotalMs] draws no ring rather than a full
 * one. That happens when an older phone is talking to a newer watch, and a ring
 * stuck at full for the whole rest would be worse than no ring at all.
 */
@Composable
fun RestPage(
    state: WearWorkoutState,
    remainingSeconds: Int?,
    modifier: Modifier = Modifier,
    /** Room the pager reserves for its edge button. Zero when there is none. */
    bottomInset: Dp = 0.dp,
) {
    ArcFrame(
        progress = state.restProgress(remainingSeconds),
        colour = MaterialTheme.colorScheme.tertiary,
        bottomInset = bottomInset,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.wear_resting),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = remainingSeconds?.toString() ?: "—",
            style = RepForthNumeric.lg,
            color = MaterialTheme.colorScheme.tertiary,
        )

        // §11 puts the next exercise on this screen, and it is the only place
        // it belongs: it is what someone decides whether to keep resting for.
        state.nextExerciseName?.let { next ->
            Text(
                text = stringResource(R.string.wear_next_up, next),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The second page: everything that is a decision rather than an action.
 *
 * Pause, skip this set, and leave this exercise all belong to the moment between
 * efforts, and all three were competing with the number during one. A swipe
 * away is the right distance for them — reachable without thought, impossible
 * to hit while racking a bar.
 *
 * "Exercise 3 of 6" lives here rather than on the set page for the same reason
 * the rim arc is wordless: this is where there is room to be specific and a
 * moment to read it.
 */
@Composable
fun ControlsPage(
    state: WearWorkoutState,
    enabled: Boolean,
    onAction: (WearAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paused = state.phase == WearPhase.Paused
    val resting = state.restTotalMs != null

    // The one page that scrolls, and the only one that should. Three full-width
    // buttons and a label do not fit a 226dp circle at 200% font scale, and §13
    // requires them to stay reachable rather than merely present. The set page
    // must never scroll: it is a glance, and a glance that moves is not one.
    Scrollable(modifier) {
        if (state.exerciseCount > 0) {
            Text(
                text = stringResource(
                    R.string.wear_exercise_of,
                    state.exerciseNumber,
                    state.exerciseCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Outlined rather than filled, which is the hierarchy the phone already
        // uses: the filled control is the one that logs a set, and it lives on
        // page 0 as the edge button. Two filled lime buttons here would give
        // Pause and Skip the same weight as completing a set, and would put
        // three accents on a display §12 allows one on.
        OutlinedButton(
            onClick = { onAction(if (paused) WearAction.Resume else WearAction.Pause) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CentredLabel(if (paused) R.string.wear_resume else R.string.wear_pause)
        }

        // Skipping a *set* is meaningless while resting -- there is no set in
        // progress to abandon -- so the control is simply absent rather than
        // present and refused.
        if (!resting) {
            OutlinedButton(
                onClick = { onAction(WearAction.SkipSet) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CentredLabel(R.string.wear_skip_set)
            }
        }

        // "Next exercise" was the third control here and is gone as of
        // 2026-09-10. Declining work has one shape now, and it is Skip.
        // Leaving a whole exercise means skipping the sets left on it,
        // which records each one instead of erasing them.
    }
}

/**
 * The third page: the picture, as big as the display allows.
 *
 * §3 asked for "a compact static thumbnail" and it was drawn at 56dp above the
 * exercise name, where it was too small to read a movement from and still cost
 * the number vertical room. Given a page of its own it can be the whole circle,
 * which is the size at which a picture of a movement is actually worth sending.
 *
 * §6's attribution rides with it. The notice is required wherever the imagery is
 * shown, and it is gated on the picture being *on screen* rather than on the
 * phone having sent one -- a decode that failed shows no image, and then there
 * is nothing to attribute.
 */
@Composable
fun MediaPage(
    state: WearWorkoutState,
    thumbnail: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = thumbnail,
            // Decorative: the exercise name is on the page before this one, and
            // a screen reader announcing the picture as well would say the same
            // thing twice.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(MEDIA_INSET).clip(CircleShape),
        )

        state.mediaAttribution?.let { notice ->
            // On a scrim, because the notice is required to be legible and the
            // imagery it sits on is not ours to choose. Most of these pictures
            // are a figure on a near-white studio background, and quiet grey
            // text on that is a notice in name only.
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = PADDING_V)
                    .background(
                        MaterialTheme.colorScheme.background.copy(alpha = NOTICE_SCRIM),
                        RoundedCornerShape(NOTICE_RADIUS),
                    )
                    .padding(horizontal = NOTICE_PADDING_H, vertical = NOTICE_PADDING_V),
            )
        }
    }
}

/**
 * A button label in the middle of its button.
 *
 * Wear's `Button` lays its content out as a row starting after an icon slot, so
 * a bare `Text` sits hard against the left of a full-width button — which on a
 * circle puts it where the pill is narrowest and looks like a mistake.
 */
@Composable
private fun RowScope.CentredLabel(label: Int) {
    Text(
        text = stringResource(label),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f),
    )
}

// ------------------------------------------------------------------ scaffold

/**
 * Content in the middle of the circle, with a progress arc around the rim.
 *
 * The arc is the display's largest feature and the one a round screen has that a
 * rectangle does not. A [progress] of null draws no arc at all, which is what
 * happens when the phone is too old to send the numbers it needs: an empty rim
 * says nothing, and a rim stuck at zero or full says something false.
 */
@Composable
private fun ArcFrame(
    progress: Float?,
    colour: Color,
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (progress != null) {
            ProgressRim(
                progress = progress,
                colour = colour,
                // The palette has a role for the unfilled part of a rail, and
                // Wear's default was a dimmed copy of the accent -- an amber
                // rest ring on an olive track, two colours where §12 allows one.
                track = LocalRepForthColors.current.track,
            )
        }
        // The inset shrinks the box the content is centred *in*, rather than
        // padding the content itself. Padding the column made it taller and the
        // centring then moved everything up by half the inset, which pushed the
        // exercise name off the top of the circle while still leaving the last
        // line under the button. The arc is outside this box and keeps the rim.
        // Top padding as well as the edge button's. Content centred in a box
        // that starts at the very top of the circle sits where the circle is
        // narrowest, and the first line came back clipped by the glass even
        // though the layout had room for it.
        //
        // **Scrollable, and at ordinary sizes it never moves.** The design pass
        // set out to stop the wrist scrolling for its controls, and it does --
        // they are a page away now, and the primary action is an edge button
        // pinned outside this box, so neither is ever behind a scroll. What is
        // inside is text, and at 200% font scale on a 226dp circle four lines of
        // it do not fit. Without this the set position was simply cut off with
        // no way to reach it, which is worse than the scroll that was removed
        // and is exactly what §13 forbids.
        val scroll = rememberScrollState()

        // **The active page's requester, not one per page.** In a pager several
        // pages are composed at once, and each of these used to call
        // `requestFocus()` from its own `LaunchedEffect` the moment it appeared
        // -- so the crown could end up driving a page nobody was looking at, and
        // three composables competed for one focus on every page change.
        // `rememberActiveFocusRequester` only requests when its focus group is
        // the active one, which is what the pager below marks.
        val focus = rememberActiveFocusRequester()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = ARC_TOP_INSET, bottom = bottomInset)
                .rotaryScrollable(RotaryScrollableDefaults.behavior(scroll), focus)
                .verticalScroll(scroll),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ARC_CONTENT_INSET),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SPACING, Alignment.CenterVertically),
                content = content,
            )
        }
    }
}

/**
 * The arc on the rim, drawn rather than assembled from a component.
 *
 * **Wear's `CircularProgressIndicator` animates a change in progress, and that
 * makes it useless for a countdown.** Found on hardware: the rest ring "looks
 * always full". The data was correct all along — the watch had a 30,000ms total
 * and 29,966ms remaining, exactly right — and so was the arithmetic. What was
 * wrong was that a ring told to move from 30 seconds to 3 had travelled 16% of
 * the way and was still short after five seconds of animation. A value that
 * changes every second never arrives anywhere.
 *
 * The workout arc looked fine for the same reason it was fine: it changes once
 * per set, in steps big enough that the animation finishes before the next one.
 *
 * Two arcs and no state, which is also the cheapest thing that can draw this.
 * The sweep starts at twelve o'clock because that is where a wearer looks for
 * the top of a dial.
 */
@Composable
private fun ProgressRim(progress: Float, colour: Color, track: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = ARC_STROKE.toPx()
        val inset = ARC_INSET.toPx() + width / 2f
        val stroke = Stroke(width = width, cap = StrokeCap.Round)
        val topLeft = Offset(inset, inset)
        val arc = Size(size.width - inset * 2f, size.height - inset * 2f)

        drawArc(
            color = track,
            startAngle = TWELVE_OCLOCK,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = stroke,
        )
        drawArc(
            color = colour,
            startAngle = TWELVE_OCLOCK,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = stroke,
        )
    }
}

/** `drawArc` measures from three o'clock, so the top of the dial is -90. */
private const val TWELVE_OCLOCK = -90f

/**
 * The number, with its unit beside it rather than under it.
 *
 * §12 is explicit about the order to sacrifice things in: "Nothing numeric
 * renders below xs (20sp); if a figure will not fit, cut the label instead."
 * Once the exercise name needed two lines, a stacked unit was the line that had
 * to go — and putting it alongside keeps both facts for the cost of neither.
 * Without it "12" and "42" are the same picture, one a rep count and one a
 * countdown.
 *
 * The unit sits on the bottom edge, which is where a unit belongs against a
 * numeral whose leading is tight.
 */
@Composable
private fun HeroNumber(value: String, unit: Int, colour: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = value, style = RepForthNumeric.lg, color = colour)
        Text(
            text = stringResource(unit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = UNIT_GAP, bottom = UNIT_BASELINE),
        )
    }
}

/**
 * The controls column, which may be taller than the watch.
 *
 * Centred while it fits and scrollable when it does not, which is what
 * `fillMaxSize().verticalScroll()` gives: the scroll modifier relaxes the
 * maximum height to infinity and leaves the minimum at the viewport, so
 * [Arrangement.spacedBy] with [Alignment.CenterVertically] still centres a short
 * page. The crown drives it, which §11 asks for.
 */
@Composable
private fun Scrollable(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val focus = rememberActiveFocusRequester()

    Column(
        modifier = modifier
            .fillMaxSize()
            .rotaryScrollable(RotaryScrollableDefaults.behavior(scroll), focus)
            .verticalScroll(scroll)
            .padding(horizontal = PADDING_H, vertical = PADDING_V),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SPACING, Alignment.CenterVertically),
        content = content,
    )
}

/**
 * One column, centred, with room at the edges.
 *
 * A round screen clips its corners, so nothing may sit against the edge and
 * everything is centred rather than start-aligned. The padding is generous for
 * that reason and not for taste.
 */
@Composable
private fun Centred(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PADDING_H, vertical = PADDING_V),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SPACING, Alignment.CenterVertically),
            content = content,
        )
    }
}

// -------------------------------------------------------------- the arithmetic

/**
 * How far through the whole workout, or null when the phone did not say.
 *
 * Counted in sets rather than exercises so the sweep is even; see
 * [WearWorkoutState.setsCompleted]. Coerced because a projection that ever
 * disagreed with itself should draw a full ring rather than throw on a wrist.
 */
private fun WearWorkoutState.workoutProgress(): Float? {
    if (setsTotal <= 0) return null
    return (setsCompleted.toFloat() / setsTotal).coerceIn(0f, 1f)
}

/**
 * How much rest is left, as a fraction of the rest prescribed.
 *
 * Null when either half is missing: an older phone sends no total, and there is
 * nothing to count against before the clock is armed.
 */
private fun WearWorkoutState.restProgress(remainingSeconds: Int?): Float? {
    val total = restTotalMs ?: return null
    val left = remainingSeconds ?: return null
    if (total <= 0L) return null
    return (left * 1000f / total).coerceIn(0f, 1f)
}

/** Enough to keep the required notice legible over any upstream picture. */
private const val NOTICE_SCRIM = 0.72f
private val NOTICE_RADIUS = 8.dp
private val NOTICE_PADDING_H = 10.dp
private val NOTICE_PADDING_V = 4.dp

/** Big enough to read a movement from, which 56dp above a title never was. */
private val MEDIA_INSET = 14.dp

/**
 * How far in from the glass the arc sits, and how thick it is.
 *
 * The rim of a round watch is where a bezel would be, and drawing right at the
 * edge on a display with any curvature loses the stroke. 6dp in is enough on the
 * Ultra and on the 180dp stress size.
 */
private val ARC_INSET = 6.dp
private val ARC_STROKE = 5.dp

/**
 * Content stays well inside the arc.
 *
 * The circle's usable width at the vertical extremes is far less than its
 * diameter, and text centred in a box does not know that. This keeps a line of
 * it off the ring.
 */
private val ARC_CONTENT_INSET = 30.dp

/** Extra for the line that sits highest, where the circle is narrowest. */
private val NAME_INSET = 16.dp

/** Keeps the topmost line out of the curve. */
private val ARC_TOP_INSET = 20.dp

/** The unit against the numeral: close, and sitting on its baseline. */
private val UNIT_GAP = 4.dp
private val UNIT_BASELINE = 8.dp

private val PADDING_H = 20.dp

/**
 * Deep, because a round display narrows towards the top and bottom.
 *
 * 12dp was not enough and it showed: the thumbnail at the top of the exercise
 * screen came back clipped on the real watch, and so did the first and last
 * buttons. The middle of a circle is 226dp wide and the last few rows are not,
 * so a column that starts at the very edge starts inside the curve.
 */
private val PADDING_V = 28.dp
private val SPACING = 6.dp
