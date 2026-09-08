package com.repforth.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ChildButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
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
 */

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
 */
@Composable
fun ExerciseScreen(
    state: WearWorkoutState,
    remainingSeconds: Int?,
    enabled: Boolean,
    onAction: (WearAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scrollable(modifier) {
        Text(
            text = state.exerciseName,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val holdMs = state.targetDurationMs
        if (holdMs != null) {
            // The clock takes the big slot, exactly as it does on the phone.
            // During a plank the only number worth that space is how long is
            // left; the prescription is merely what the seconds started at.
            Text(
                text = (remainingSeconds ?: (holdMs / 1000L).toInt()).toString(),
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = stringResource(R.string.wear_seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.wear_set_of, state.setNumber, state.totalSets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.wear_set_of, state.setNumber, state.totalSets),
                style = MaterialTheme.typography.displaySmall,
            )
            state.targetReps?.let { reps ->
                Text(
                    text = stringResource(R.string.wear_target_reps, reps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Complete is the button the whole app exists for, so it is the wide
            // one and it is first. §12's out-of-breath-with-chalk argument is
            // even truer on a wrist than on a phone.
            Button(
                onClick = { onAction(WearAction.CompleteSet) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.wear_complete_set))
            }
        }

        // Both kinds keep these. Stopping early is still allowed for timed work
        // — it is a skip, which is a row in the history and not an absence.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = {
                    onAction(if (state.phase == WearPhase.Paused) WearAction.Resume else WearAction.Pause)
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(
                        if (state.phase == WearPhase.Paused) {
                            R.string.wear_resume
                        } else {
                            R.string.wear_pause
                        },
                    ),
                    maxLines = 1,
                )
            }
            Button(
                onClick = { onAction(WearAction.SkipSet) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.wear_skip_set), maxLines = 1)
            }
        }

        NextExerciseButton(enabled = enabled, onAction = onAction)
    }
}

/** §11 screen 4: counting down between sets. */
@Composable
fun RestScreen(
    state: WearWorkoutState,
    remainingSeconds: Int?,
    enabled: Boolean,
    onAction: (WearAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scrollable(modifier) {
        Text(
            text = stringResource(R.string.wear_resting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = remainingSeconds?.toString() ?: "—",
            style = MaterialTheme.typography.displayLarge,
        )

        // §11 puts the next exercise on this screen, and it is the only place
        // it belongs: it is what someone decides whether to keep resting for.
        state.nextExerciseName?.let { next ->
            Text(
                text = stringResource(R.string.wear_next_up, next),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Button(
            onClick = { onAction(WearAction.SkipRest) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.wear_skip_rest))
        }

        NextExerciseButton(enabled = enabled, onAction = onAction)
    }
}

/**
 * Leave this exercise, abandoning whatever sets remain on it.
 *
 * **It had no button at all, on either screen.** `WearAction.NextExercise` has
 * existed since the protocol was written, maps to a phone command, and §3 lists
 * it in the watch MVP — and nothing could send it. The protocol's own standard
 * for the action set is "nothing duplicated and nothing unreachable"; this was
 * the unreachable half, and it is the same shape as the exclusions the phone
 * enforced for months with no way to edit them.
 *
 * A [ChildButton] rather than a filled one, and last, because that is the
 * emphasis the phone gives it: Log set is filled, Pause and Skip set are
 * outlined, and Next exercise sits below both as a text button. The hierarchy is
 * not decoration on a wrist — this is the control that throws away the sets you
 * have not done yet, and it should be the hardest of the three to hit by
 * accident while out of breath.
 *
 * Offered while resting as well as during a set, again matching the phone, whose
 * condition is `isActive || isResting`. The rest screen is where someone is
 * actually deciding what to do next, and it already names what is coming.
 *
 * On the last exercise this finishes the workout rather than being refused —
 * `SessionEngine.nextExercise` moves to `COMPLETING` — so there is no state in
 * which it is offered and does nothing.
 */
@Composable
private fun NextExerciseButton(enabled: Boolean, onAction: (WearAction) -> Unit) {
    ChildButton(
        onClick = { onAction(WearAction.NextExercise) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.wear_next_exercise), maxLines = 1)
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

/**
 * One column, centred, with room at the edges.
 *
 * A round screen clips its corners, so nothing may sit against the edge and
 * everything is centred rather than start-aligned. The padding is generous for
 * that reason and not for taste.
 *
 * For the three screens that are only ever a sentence or two. The two that carry
 * controls use [Scrollable] instead, which is this plus somewhere for the
 * content to go when it does not fit.
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

/**
 * The same column, but it can be taller than the watch.
 *
 * Centred while the content fits and scrollable when it does not, which is what
 * `fillMaxSize().verticalScroll()` gives: the scroll modifier relaxes the
 * maximum height to infinity and leaves the minimum at the viewport, so
 * [Arrangement.spacedBy] with [Alignment.CenterVertically] still centres a short
 * screen.
 *
 * Two things made this necessary at once. A third control does not fit — the
 * exercise screen already spent about 194 of its 216dp — and **neither of these
 * screens survived 200% font scaling before**, which §13 requires: the old
 * container was a fixed-size box, so anything that grew past the display was
 * simply cut off with no way to reach it.
 *
 * The crown scrolls it, which §11 asks for ("rotary scrolling where
 * appropriate"). Rotary needs the scrollable to hold focus and there is nothing
 * else on these screens competing for it, so the request is unconditional.
 */
@Composable
private fun Scrollable(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

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

private val PADDING_H = 20.dp
private val PADDING_V = 12.dp
private val SPACING = 6.dp
