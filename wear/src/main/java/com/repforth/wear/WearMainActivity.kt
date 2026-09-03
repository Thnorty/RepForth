package com.repforth.wear

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.AppScaffold
import dagger.hilt.android.AndroidEntryPoint
import com.repforth.core.wearprotocol.WearWorkoutState
import com.repforth.core.wearprotocol.restRemainingMs
import kotlinx.coroutines.delay

/**
 * The watch app: one activity, five screens, no engine (§11).
 *
 * There is no navigation graph because there is nowhere to navigate. Which
 * screen shows is a function of the last snapshot the phone sent and whether
 * the phone can still be heard — a user cannot browse to the rest screen, they
 * arrive there because a set was completed.
 */
@AndroidEntryPoint
class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

@Composable
private fun WearApp(viewModel: WearViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Reachability can change while nothing is on screen, so it is re-checked
    // when the composition starts rather than trusted from the last visit.
    LaunchedEffect(Unit) { viewModel.onResumed() }

    MaterialTheme {
        AppScaffold {
            when (state.screen) {
                WearScreen.Disconnected -> DisconnectedScreen(lastSeen = state.workout)

                WearScreen.NoWorkout -> NoWorkoutScreen()

                WearScreen.Exercise -> state.workout?.let { workout ->
                    ExerciseScreen(
                        state = workout,
                        enabled = state.controlsEnabled,
                        onAction = viewModel::onAction,
                    )
                }

                WearScreen.Rest -> state.workout?.let { workout ->
                    RestScreen(
                        state = workout,
                        remainingSeconds = rememberRestSeconds(workout),
                        enabled = state.controlsEnabled,
                        onAction = viewModel::onAction,
                    )
                }

                WearScreen.Finished -> state.workout?.let { workout ->
                    FinishedScreen(state = workout)
                }
            }
        }
    }
}

/**
 * The rest countdown, ticked on the watch's own clock.
 *
 * The phone sends a deadline and the clock reading it was taken against, both
 * on the phone's `elapsedRealtime`. Subtracting those two gives a **duration**,
 * which is the only thing that means the same on both devices, and the watch
 * then counts that duration down against its own clock.
 *
 * **The obvious version of this is badly wrong.** Comparing the phone's
 * deadline directly with the watch's `elapsedRealtime` returns the difference
 * in how long the two devices have been switched on. On the first hardware
 * test that displayed a 60-second rest as **591092** — the phone had been up
 * 595515 seconds and the watch 4465 — and the kdoc here previously called it
 * "wrong by a constant" and "close enough for a rest timer". It was neither.
 *
 * What remains is the transfer latency, about a quarter of a second, applied
 * once at arrival rather than accumulating. The phone stays the authority and
 * its next snapshot resets the count.
 */
@Composable
private fun rememberRestSeconds(state: WearWorkoutState): Int? {
    val remainingAtPublish = state.restRemainingMs() ?: return null

    // Anchored to the snapshot: a new revision restarts the countdown from
    // whatever the phone last said, rather than continuing an old one.
    val localDeadline = remember(state.revision, remainingAtPublish) {
        SystemClock.elapsedRealtime() + remainingAtPublish
    }

    var remaining by remember(localDeadline) { mutableIntStateOf(secondsUntil(localDeadline)) }

    LaunchedEffect(localDeadline) {
        while (remaining > 0) {
            delay(TICK_MS)
            remaining = secondsUntil(localDeadline)
        }
    }

    return remaining
}

private fun secondsUntil(localDeadlineMs: Long): Int =
    ((localDeadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L) / 1000L).toInt()

private const val TICK_MS = 250L
