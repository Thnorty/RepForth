package com.repforth.feature.builder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.repforth.core.designsystem.component.MuscleSelector
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.LocalRepForthColors
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Stroke
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.exercisedata.labelRes
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.BodyView
import com.repforth.core.model.Muscle

/**
 * Coach's generation entry point (§3, §8).
 *
 * §12 makes Coach a mode inside the builder, not a screen beside it, and this is
 * what that means in practice: it collects one optional answer, hands it to the
 * validated provider pipeline, and drops the result into the builder as ordinary
 * editable rows. Nothing is saved on the way through. A generated plan is a
 * starting point, and the user gets the last word on every number in it.
 *
 * Deliberately one question. The profile already knows the goal, the experience,
 * the session length and the equipment; asking again here would be asking
 * someone to repeat themselves, and disagreeing with what they said in
 * onboarding is worse than not asking at all.
 */
@Composable
internal fun CoachScreen(
    state: BuilderUiState,
    onMuscleToggled: (Muscle) -> Unit,
    onRegionToggled: (BodyRegion) -> Unit,
    onGenerate: (String) -> Unit,
    onCancelGenerate: () -> Unit,
    onDismissError: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf(BodyView.FRONT) }
    var confirmingCancelGeneration by rememberSaveable { mutableStateOf(false) }
    val defaultName = stringResource(R.string.coach_default_name)

    BackHandler(enabled = true) {
        if (state.generating) {
            confirmingCancelGeneration = true
        } else {
            onClose()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Layout.gutterPhone, vertical = Space.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            Text(
                text = stringResource(R.string.coach_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (state.generating) {
                        confirmingCancelGeneration = true
                    } else {
                        onClose()
                    }
                },
                modifier = Modifier.heightIn(min = Target.min),
            ) {
                Icon(
                    painter = RfIcons.Close,
                    contentDescription = stringResource(R.string.coach_close),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                horizontal = Layout.gutterPhone,
                vertical = Space.s3,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            if (state.generating) {
                item(key = "generating") {
                    CoachGeneratingState()
                }
            } else {
                item(key = "hint") {
                    Text(
                        // Says what empty means, because empty is the useful
                        // default and an untouched body map otherwise reads as
                        // an unanswered question.
                        text = stringResource(R.string.coach_any),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item(key = "map") {
                    MuscleSelector(
                        selected = state.coachMuscles,
                        view = view,
                        onViewChange = { view = it },
                        onMuscleToggled = onMuscleToggled,
                        onRegionToggled = onRegionToggled,
                        labelOf = { stringResource(it.labelRes) },
                    )
                }

                state.coachFailure?.let { failure ->
                    item(key = "failure") {
                        Text(
                            text = stringResource(failure.messageRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        if (!state.generating) {
            Button(
                onClick = { onGenerate(defaultName) },
                modifier = Modifier
                    .padding(horizontal = Layout.gutterPhone, vertical = Space.s3)
                    .fillMaxWidth()
                    .heightIn(min = Target.min),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                ) {
                    Icon(
                        painter = RfIcons.Generate,
                        contentDescription = null,
                        modifier = Modifier.size(Space.s5),
                    )
                    Text(
                        text = stringResource(R.string.coach_generate),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }

    state.coachError?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(error.titleRes)) },
            text = { Text(stringResource(error.messageRes)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!error.canRetry) {
                            onDismissError()
                            return@TextButton
                        }
                        onDismissError()
                        onGenerate(defaultName)
                    },
                ) {
                    Text(
                        stringResource(
                            if (error.canRetry) {
                                R.string.coach_error_retry
                            } else {
                                R.string.coach_error_dismiss
                            },
                        ),
                    )
                }
            },
            dismissButton = if (error.canRetry) {
                {
                    TextButton(onClick = onDismissError) {
                        Text(stringResource(R.string.coach_error_dismiss))
                    }
                }
            } else {
                null
            },
        )
    }

    if (confirmingCancelGeneration) {
        AlertDialog(
            onDismissRequest = { confirmingCancelGeneration = false },
            title = { Text(stringResource(R.string.coach_cancel_dialog_title)) },
            text = { Text(stringResource(R.string.coach_cancel_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingCancelGeneration = false
                    onCancelGenerate()
                    onClose()
                }) {
                    Text(stringResource(R.string.coach_cancel_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingCancelGeneration = false }) {
                    Text(stringResource(R.string.coach_cancel_dialog_dismiss))
                }
            },
        )
    }
}

/**
 * A prominent, honest indeterminate state: providers expose no real percentage.
 */
@Composable
private fun CoachGeneratingState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Space.s6, vertical = Space.s10),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Space.s20),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = LocalRepForthColors.current.track,
                    strokeWidth = Stroke.ring,
                )
                Icon(
                    painter = RfIcons.Generate,
                    contentDescription = null,
                    modifier = Modifier.size(Space.s8),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.coach_generating_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.coach_generating_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * What to tell someone when nothing could be built.
 *
 * Each names the constraint that did the blocking and where to change it, since
 * every one of these is fixable and none of them is fixable from this screen.
 */
private val CoachFailure.messageRes: Int
    get() = when (this) {
        CoachFailure.NO_PROFILE -> R.string.coach_failed_no_profile
        CoachFailure.EQUIPMENT -> R.string.coach_failed_equipment
        CoachFailure.EXCLUSIONS -> R.string.coach_failed_exclusions
        CoachFailure.MUSCLES -> R.string.coach_failed_muscles
        CoachFailure.NOTHING -> R.string.coach_failed_nothing
    }
