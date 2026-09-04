package com.repforth.feature.session

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Asks which workout the user meant.
 *
 * Neither answer is automatic. Silently resuming the running one was the
 * original bug; silently discarding a half-finished workout to honour a tap
 * would be a worse one. Both answers lead into a workout — the running one or
 * the newly started one — so this is a fork in the road rather than a barrier.
 *
 * It names the workout in the way. "A workout is already running" is true and
 * useless if the reason someone is confused is that they have forgotten which
 * one; the name is the whole content of the answer they need.
 *
 * One composable for two callers. The normal one is the app shell, which raises
 * this *before* leaving the plan list, so nobody is dropped into a workout they
 * did not pick. The other is the session screen itself, which is reachable
 * after process death: the navigation state is restored with the plan that was
 * tapped, and the running workout is still in the database.
 *
 * **Back and a tap outside both dismiss it**, which is a third answer rather
 * than a way of skipping the question: "never mind, I did not mean to tap
 * that". It was blocked at first on the grounds that both buttons are
 * consequential — but that reasoning was inherited from when this appeared
 * *inside* the workout, over a screen showing a workout the user had not
 * chosen, where there was nowhere safe to be dropped. Raised from the plan list
 * there is: exactly where they already are. Cancelling starts nothing, ends
 * nothing and goes nowhere.
 *
 * @param runningName the workout in progress, or null if it was not started
 *   from a plan and so has no name of its own.
 * @param onCancel dismissed without answering. Must leave the running workout
 *   alone — it is the one outcome that changes nothing at all.
 */
@Composable
fun WorkoutConflictDialog(
    runningName: String?,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        // Back and outside-tap both arrive here; `AlertDialog` allows each by
        // default, so wiring this is the whole of making it dismissable.
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_conflict_title)) },
        text = {
            Text(
                stringResource(
                    R.string.session_conflict_message,
                    runningName ?: stringResource(R.string.session_conflict_unnamed),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onKeep) {
                Text(stringResource(R.string.session_conflict_keep))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.session_conflict_discard))
            }
        },
    )
}
