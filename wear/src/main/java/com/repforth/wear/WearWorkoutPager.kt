package com.repforth.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.foundation.pager.HorizontalPager
import com.repforth.core.wearprotocol.WearAction
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState

/**
 * The live workout, as pages rather than a scroll.
 *
 * **A wrist would rather swipe than scroll**, and the old layout made it scroll:
 * every control the screen offered was stacked in one column that ran off the
 * bottom of the display, so completing a set and skipping one were the same
 * gesture apart. Wear's own idiom is a page per thing, and the platform reserves
 * only the *left edge* for dismissal, so horizontal paging costs nothing.
 *
 * Two pages always, three when there is a picture:
 *
 * | Page | What it is for |
 * |---|---|
 * | 0 | The set or the rest. One number, one action. |
 * | 1 | Pause, skip, next exercise. The between-sets decisions. |
 * | 2 | The exercise image, as big as the circle allows. |
 *
 * The media page is reachable **during a set**, not only while resting, which is
 * a deliberate answer to a question that was asked: someone unsure of a movement
 * is unsure of it with the bar in their hands, and that is exactly when a
 * picture is worth having.
 *
 * The primary action is an [EdgeButton] on page 0 and nothing anywhere else. It
 * follows the bottom curve, which makes it both the largest touch target on the
 * display and the one a thumb reaches without looking.
 */
@Composable
fun ExerciseScreen(
    state: WearWorkoutState,
    remainingSeconds: Int?,
    enabled: Boolean,
    onAction: (WearAction) -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: ImageBitmap? = null,
) {
    WorkoutPages(
        state = state,
        enabled = enabled,
        onAction = onAction,
        thumbnail = thumbnail,
        modifier = modifier,
        // A timed set has no Complete button at all, and that is the engine's
        // rule rather than this screen's taste: `CompleteSet` is refused for a
        // duration target, because timed work is measured and not declared. A
        // disabled control would say "not yet"; this one is never coming.
        primaryAction = if (state.targetDurationMs != null) {
            null
        } else {
            PrimaryAction(R.string.wear_complete_set, WearAction.CompleteSet)
        },
    ) {
        ExercisePage(state = state, remainingSeconds = remainingSeconds, bottomInset = it)
    }
}

/**
 * The same frame around a rest.
 *
 * Resume while paused, skip while running. The engine refuses a `SkipRest`
 * during a pause, so offering it here would be a button that does nothing — and
 * the only way out of a paused rest is to resume it.
 */
@Composable
fun RestScreen(
    state: WearWorkoutState,
    remainingSeconds: Int?,
    enabled: Boolean,
    onAction: (WearAction) -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: ImageBitmap? = null,
) {
    val paused = state.phase == WearPhase.Paused

    WorkoutPages(
        state = state,
        enabled = enabled,
        onAction = onAction,
        thumbnail = thumbnail,
        modifier = modifier,
        primaryAction = if (paused) {
            PrimaryAction(R.string.wear_resume, WearAction.Resume)
        } else {
            PrimaryAction(R.string.wear_skip_rest, WearAction.SkipRest)
        },
    ) {
        RestPage(state = state, remainingSeconds = remainingSeconds, bottomInset = it)
    }
}

/** The one filled control page 0 offers, or null when the phase has none. */
private data class PrimaryAction(val label: Int, val action: WearAction)

@Composable
private fun WorkoutPages(
    state: WearWorkoutState,
    enabled: Boolean,
    onAction: (WearAction) -> Unit,
    thumbnail: ImageBitmap?,
    primaryAction: PrimaryAction?,
    modifier: Modifier = Modifier,
    mainPage: @Composable (bottomInset: Dp) -> Unit,
) {
    val pageCount = if (thumbnail != null) 3 else 2
    val pager = rememberPagerState { pageCount }

    HorizontalPagerScaffold(
        pagerState = pager,
        modifier = modifier,
        pageIndicator = { HorizontalPageIndicator(pagerState = pager) },
    ) {
        HorizontalPager(state = pager) { page ->
            AnimatedPage(pageIndex = page, pagerState = pager) {
                when (page) {
                    // The edge button is placed here rather than handed to
                    // `ScreenScaffold`, whose slot exists to reserve room for
                    // one even when there is none -- and a timed set has none.
                    // An empty reserved strip on a 226dp circle is a real cost.
                    // The button is placed here rather than handed to
                    // `ScreenScaffold`, whose every overload wants a scroll
                    // state to drive the button in and out -- and this page
                    // deliberately does not scroll. The page is told how much
                    // room to leave instead, which is the only thing the
                    // scaffold would have done for it.
                    0 -> Box(modifier = Modifier.fillMaxSize()) {
                        mainPage(if (primaryAction == null) 0.dp else EDGE_BUTTON_ROOM)
                        if (primaryAction != null) {
                            EdgeButton(
                                onClick = { onAction(primaryAction.action) },
                                enabled = enabled,
                                buttonSize = EdgeButtonSize.Medium,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) {
                                Text(stringResource(primaryAction.label), maxLines = 1)
                            }
                        }
                    }

                    1 -> ControlsPage(state = state, enabled = enabled, onAction = onAction)

                    else -> thumbnail?.let { image ->
                        MediaPage(state = state, thumbnail = image)
                    }
                }
            }
        }
    }
}

/**
 * How much of the bottom the edge button claims.
 *
 * **Measured off a render, not read off the API.** A medium `EdgeButton` is
 * documented as 52dp of pill, and it occupies 73dp of the display: it is a
 * shape that grows downward into the curve, so the pill height is not the
 * space it takes. 56dp was guessed from the documented figure and left the
 * last line of the page underneath "Complete" — visibly, and only once the
 * exercise name went to two lines and pushed it down there.
 *
 * If the button size changes, measure again rather than adjusting by eye:
 * find the topmost row of the golden that is a wide band of the accent.
 */
private val EDGE_BUTTON_ROOM = 76.dp
