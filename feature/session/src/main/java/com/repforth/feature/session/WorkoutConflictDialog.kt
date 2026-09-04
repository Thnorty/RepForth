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
 * @param runningName the workout in progress, or null if it was not started
 *   from a plan and so has no name of its own.
 */
@Composable
fun WorkoutConflictDialog(
    runningName: String?,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        // No dismiss-by-tapping-away: both answers are consequential, and one
        // of them ends a workout.
        onDismissRequest = {},
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
