package com.repforth.feature.builder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.repforth.core.designsystem.component.MuscleSelector
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Radius
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Stroke
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.exercisedata.labelRes
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.BodyView
import com.repforth.core.model.Muscle
import com.repforth.core.model.WorkoutLimits
import kotlinx.coroutines.delay

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
    onDaysChange: (Int) -> Unit,
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

            item(key = "days") {
                CoachDaySelector(
                    days = state.coachDays,
                    enabled = !state.generating,
                    onDaysChange = onDaysChange,
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

    state.coachError?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(error.titleRes)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    Text(
                        text = error.waitedSeconds
                            ?.let { stringResource(error.messageRes, it) }
                            ?: stringResource(error.messageRes),
                    )
                    error.detail?.let { detail ->
                        ProviderResponseBlock(detail)
                    }
                }
            },
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
            // Copy sits with the dialog's other actions rather than beside the
            // block it copies. AlertDialog offers two button slots, so it
            // shares this one with Dismiss; leftmost, because it is the least
            // final thing here and neither closes the dialog.
            dismissButton = if (error.canRetry || error.detail != null) {
                {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.s1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        error.detail?.let { detail -> CopyResponseButton(detail) }
                        if (error.canRetry) {
                            TextButton(onClick = onDismissError) {
                                Text(stringResource(R.string.coach_error_dismiss))
                            }
                        }
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
 * The server's reply, quoted verbatim.
 *
 * Selectable and copyable because this is the one thing on screen the user may
 * need to take somewhere else — a provider's status page, a bug report, or a
 * message to whoever runs their local model server. It is presented as a
 * quotation throughout: the label sits outside the block so the block holds only
 * the server's own bytes, and since §8 stopped inspecting the address this text
 * comes from whatever host the user configured and must never read as the app
 * speaking.
 */
@Composable
private fun ProviderResponseBlock(
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        Text(
            text = stringResource(R.string.coach_error_detail_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Verbatim, monospace, scrollable both ways: JSON does not wrap
        // sensibly, and a long payload must not push the dialog's buttons off
        // screen. Selectable, because the user may need to take it elsewhere.
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = Layout.dialogCodeMaxHeight)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(Space.s3),
                )
            }
        }
    }
}

/**
 * Puts the server's reply on the clipboard.
 *
 * Below API 33 the system shows no copy confirmation of its own, and this app
 * supports 28 — so the label saying "Copied" is the only feedback some devices
 * give, and it is announced politely for screen readers.
 */
@Composable
private fun CopyResponseButton(detail: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(detail) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }

    TextButton(
        onClick = {
            clipboard.setText(AnnotatedString(detail))
            copied = true
        },
        modifier = Modifier
            .heightIn(min = Target.min)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            stringResource(
                if (copied) {
                    R.string.coach_error_detail_copied
                } else {
                    R.string.coach_error_detail_copy
                },
            ),
        )
    }
}

private const val COPIED_FEEDBACK_MS = 2_000L

/**
 * How many days to build.
 *
 * One is a first-class answer, not an edge case: it produces a single workout
 * saved on its own, which is what someone asking for "a chest session today"
 * means. The summary line under the control says which of the two will happen,
 * because the difference only becomes visible after generating otherwise.
 *
 * A row of buttons rather than a slider: onboarding already shipped a slider
 * whose sixth value could not be selected on a real phone, and seven discrete
 * choices do not need a continuous control.
 */
@Composable
private fun CoachDaySelector(
    days: Int,
    enabled: Boolean,
    onDaysChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Text(
            text = stringResource(R.string.coach_days_label),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s1),
        ) {
            WorkoutLimits.days.forEach { option ->
                val selected = option == days
                val label = stringResource(R.string.coach_days_value, option)
                FilterChip(
                    selected = selected,
                    onClick = { onDaysChange(option) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Target.min)
                        .semantics { contentDescription = label },
                )
            }
        }
        Text(
            text = pluralStringResource(R.plurals.coach_days_summary, days, days),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Animated action button that provides pulsing glow and breathing motion
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

