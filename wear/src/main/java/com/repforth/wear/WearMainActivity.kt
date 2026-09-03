package com.repforth.wear

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.AppScaffold
import dagger.hilt.android.AndroidEntryPoint
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
                        remainingSeconds = rememberRestSeconds(workout.deadlineElapsedRealtimeMs),
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
 * The rest countdown, ticked locally against the phone's deadline.
 *
 * §11 sends a deadline rather than a remaining duration precisely so this can
 * happen: the watch subtracts its own `elapsedRealtime` and is wrong once, by
 * the transfer latency, instead of drifting further apart every second. It also
 * means the countdown keeps moving with no traffic at all — the phone does not
 * send a message per second, and a watch out of range still counts down
 * correctly to a deadline it already has.
 *
 * Both devices measure `elapsedRealtime` from their own boot, so this is not
 * the same clock. It is close enough for a rest timer and wrong by a constant,
 * which is the trade §11 chose; the phone remains the authority, and its next
 * snapshot corrects anything that matters.
 */
@Composable
private fun rememberRestSeconds(deadlineElapsedRealtimeMs: Long?): Int? {
    if (deadlineElapsedRealtimeMs == null) return null

    var remaining by remember(deadlineElapsedRealtimeMs) {
        mutableStateOf(secondsUntil(deadlineElapsedRealtimeMs))
    }

    LaunchedEffect(deadlineElapsedRealtimeMs) {
        while (remaining > 0) {
            delay(TICK_MS)
            remaining = secondsUntil(deadlineElapsedRealtimeMs)
        }
    }

    return remaining
}

private fun secondsUntil(deadline: Long): Int =
    ((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L) / 1000L).toInt()

private const val TICK_MS = 250L
