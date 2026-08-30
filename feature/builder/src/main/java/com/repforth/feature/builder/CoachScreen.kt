package com.repforth.feature.builder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.repforth.core.designsystem.component.MuscleSelector
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
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
 * validated provider-or-rules pipeline, and drops the result into the builder as
 * ordinary editable rows. Nothing is saved on the way through. A generated plan
 * is a starting point, and the user gets the last word on every number in it.
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
                    enabled = !state.generating,
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

        CoachGenerateButton(
            generating = state.generating,
            onClick = { onGenerate(defaultName) },
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
 * Animated action button that provides pulsing lime glow and breathing motion
 * while generation is underway, transitioning text to indicate active progress.
 */
@Composable
private fun CoachGenerateButton(
    generating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "coach_glow")

    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_scale",
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )

    Box(
        modifier = modifier
            .padding(horizontal = Layout.gutterPhone, vertical = Space.s3)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick,
            enabled = !generating,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Target.min)
                .graphicsLayer {
                    if (generating) {
                        scaleX = glowScale
                        scaleY = glowScale
                    }
                }
                .drawBehind {
                    if (generating) {
                        // Outer soft aura
                        drawRoundRect(
                            color = primaryColor.copy(alpha = glowAlpha * 0.35f),
                            topLeft = Offset(-Space.s2.toPx(), -Space.s2.toPx()),
                            size = Size(size.width + Space.s4.toPx(), size.height + Space.s4.toPx()),
                            cornerRadius = CornerRadius((size.height + Space.s4.toPx()) / 2f, (size.height + Space.s4.toPx()) / 2f),
                        )
                        // Inner glow ring
                        drawRoundRect(
                            color = primaryColor.copy(alpha = glowAlpha * 0.65f),
                            topLeft = Offset(-Space.s1.toPx(), -Space.s1.toPx()),
                            size = Size(size.width + Space.s2.toPx(), size.height + Space.s2.toPx()),
                            cornerRadius = CornerRadius((size.height + Space.s2.toPx()) / 2f, (size.height + Space.s2.toPx()) / 2f),
                        )
                    }
                }
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                if (generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Space.s5),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = Stroke.thick,
                    )
                    Text(
                        text = stringResource(R.string.coach_generating_action),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
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
