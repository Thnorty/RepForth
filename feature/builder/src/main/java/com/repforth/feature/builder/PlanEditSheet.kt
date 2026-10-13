package com.repforth.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.LocalReducedMotion
import com.repforth.core.designsystem.theme.LocalUnitSystem
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Stroke
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.designsystem.theme.formatWeight
import com.repforth.core.designsystem.theme.symbol
import com.repforth.core.media.ui.ExerciseMedia
import com.repforth.core.media.ui.ExerciseMediaSize

/**
 * Coach changing the plan already in the builder: ask, wait, review, apply.
 *
 * A sheet over the builder rather than a screen of its own, because the plan
 * being changed is the thing on screen and the review is about it. Nothing here
 * writes anything: Apply hands the proposal to the draft, and the draft's own
 * Save is still the only way into the database.
 *
 * While Coach is working the sheet cannot be swiped away; back or the scrim asks
 * first, as cancelling generation does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanEditSheet(
    state: BuilderUiState,
    onTextChange: (String) -> Unit,
    onUnpin: () -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onAskAgain: () -> Unit,
    onErrorDismissed: () -> Unit,
    onDismiss: () -> Unit,
    onOpenProviderSettings: () -> Unit,
) {
    val edit = state.planEdit
    if (edit == PlanEditState.Closed) return

    val working by rememberUpdatedState(edit is PlanEditState.Working)
    var confirmingStop by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !working },
    )

    ModalBottomSheet(
        onDismissRequest = { if (working) confirmingStop = true else onDismiss() },
        sheetState = sheetState,
    ) {
        PlanEditContent(
            state = state,
            onTextChange = onTextChange,
            onUnpin = onUnpin,
            onSend = onSend,
            onCancel = onCancel,
            onApply = onApply,
            onAskAgain = onAskAgain,
            onDismiss = onDismiss,
            onOpenProviderSettings = onOpenProviderSettings,
        )
    }

    if (edit is PlanEditState.Failed) {
        CoachErrorDialog(
            error = edit.error,
            onDismiss = onErrorDismissed,
            onRetry = { onSend(edit.instruction) },
        )
    }

    if (confirmingStop) {
        AlertDialog(
            onDismissRequest = { confirmingStop = false },
            title = { Text(stringResource(R.string.plan_edit_cancel_confirm_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingStop = false
                    onDismiss()
                }) {
                    Text(stringResource(R.string.plan_edit_cancel_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingStop = false }) {
                    Text(stringResource(R.string.coach_cancel_dialog_dismiss))
                }
            },
        )
    }
}

/**
 * The sheet's content, stateless and outside the sheet, so a screenshot and an
 * accessibility walk can render it without a popup window.
 */
@Composable
internal fun PlanEditContent(
    state: BuilderUiState,
    onTextChange: (String) -> Unit,
    onUnpin: () -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onAskAgain: () -> Unit,
    onDismiss: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Layout.gutterPhone)
            .padding(bottom = Space.s4)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        // The body scrolls and the review's actions do not: at twice the font size
        // four rows already fill the screen, and twelve can be asked for, so an
        // Apply below them would be a button nobody finds. Same arrangement as the
        // exercise detail sheet's pinned action.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            when (val edit = state.planEdit) {
                PlanEditState.Closed -> Unit
                is PlanEditState.Asking -> Asking(
                    pin = edit.pin,
                    text = edit.text,
                    isWeek = state.isWeeklyPlan,
                    configured = state.providerConfigured,
                    onTextChange = onTextChange,
                    onUnpin = onUnpin,
                    onSend = onSend,
                    onOpenProviderSettings = onOpenProviderSettings,
                )
                // Behind the error dialog the request stays on screen, so dismissing
                // the dialog lands where the person left off.
                is PlanEditState.Failed -> Asking(
                    pin = edit.pin,
                    text = edit.instruction,
                    isWeek = state.isWeeklyPlan,
                    configured = state.providerConfigured,
                    onTextChange = {},
                    onUnpin = {},
                    onSend = {},
                    onOpenProviderSettings = onOpenProviderSettings,
                )
                is PlanEditState.Working -> Working(onCancel = onCancel)
                is PlanEditState.Reviewing -> Reviewing(
                    edit = edit,
                    stale = state.planEditIsStale,
                    isWeek = state.isWeeklyPlan,
                    ceilingMinutes = state.sessionCeilingMinutes,
                )
            }
        }
        (state.planEdit as? PlanEditState.Reviewing)?.let { edit ->
            ReviewActions(
                hasChanges = edit.rows.isNotEmpty(),
                stale = state.planEditIsStale,
                onApply = onApply,
                onAskAgain = onAskAgain,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun Asking(
    pin: PinnedExercise?,
    text: String,
    isWeek: Boolean,
    configured: Boolean,
    onTextChange: (String) -> Unit,
    onUnpin: () -> Unit,
    onSend: (String) -> Unit,
    onOpenProviderSettings: () -> Unit,
) {
    Text(stringResource(R.string.plan_edit_title), style = MaterialTheme.typography.titleLarge)

    if (!configured) CoachSetupNotice(onOpenProviderSettings = onOpenProviderSettings)

    pin?.let { pinned ->
        val about = if (isWeek && pinned.dayNumber != null) {
            stringResource(R.string.plan_edit_about_day, pinned.name, pinned.dayNumber)
        } else {
            stringResource(R.string.plan_edit_about, pinned.name)
        }
        InputChip(
            selected = false,
            onClick = onUnpin,
            label = { Text(about) },
            trailingIcon = {
                Icon(
                    painter = RfIcons.Close,
                    contentDescription = stringResource(R.string.plan_edit_unpin, pinned.name),
                    modifier = Modifier.size(Space.s4),
                )
            },
            modifier = Modifier.heightIn(min = Target.min),
        )
        val swap = stringResource(R.string.plan_edit_swap_similar_instruction)
        SuggestionChip(
            onClick = { onSend(swap) },
            enabled = configured,
            label = { Text(stringResource(R.string.plan_edit_swap_similar)) },
            icon = {
                Icon(painter = RfIcons.Generate, contentDescription = null, modifier = Modifier.size(Space.s4))
            },
            modifier = Modifier.heightIn(min = Target.min),
        )
    }

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(stringResource(R.string.plan_edit_hint)) },
        minLines = 2,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = { onSend(text) },
        enabled = configured && text.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.min),
    ) {
        Text(stringResource(R.string.plan_edit_send))
    }
}

@Composable
private fun Working(onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s6)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        // An indeterminate ring never stops, so with reduced motion on there is
        // no ring at all rather than one that spins regardless.
        if (LocalReducedMotion.current) {
            Icon(painter = RfIcons.Generate, contentDescription = null, modifier = Modifier.size(Space.s8))
        } else {
            CircularProgressIndicator(modifier = Modifier.size(Space.s8), strokeWidth = Stroke.thick)
        }
        Text(
            stringResource(R.string.plan_edit_working_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.plan_edit_working_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.min),
    ) {
        Text(stringResource(R.string.plan_edit_cancel))
    }
}

@Composable
private fun Reviewing(
    edit: PlanEditState.Reviewing,
    stale: Boolean,
    isWeek: Boolean,
    ceilingMinutes: Int?,
) {
    Text(stringResource(R.string.plan_edit_review_title), style = MaterialTheme.typography.titleLarge)

    if (edit.rows.isEmpty()) {
        Text(stringResource(R.string.plan_edit_no_changes), style = MaterialTheme.typography.titleMedium)
    }
    if (edit.rationale.isNotBlank()) {
        Text(
            edit.rationale,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    edit.rows.forEach { row -> ChangeRow(row) }

    edit.dayMinutes.toSortedMap().forEach { (day, minutes) ->
        val over = ceilingMinutes != null && minutes > ceilingMinutes
        Text(
            text = when {
                isWeek && over -> stringResource(R.string.plan_edit_day_minutes_over, day, minutes, ceilingMinutes!!)
                isWeek -> stringResource(R.string.plan_edit_day_minutes, day, minutes)
                over -> stringResource(R.string.plan_edit_workout_minutes_over, minutes, ceilingMinutes!!)
                else -> stringResource(R.string.plan_edit_workout_minutes, minutes)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (stale) {
        Text(
            stringResource(R.string.plan_edit_stale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

}

@Composable
private fun ReviewActions(
    hasChanges: Boolean,
    stale: Boolean,
    onApply: () -> Unit,
    onAskAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!hasChanges) {
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Target.min),
        ) {
            Text(stringResource(R.string.plan_edit_close))
        }
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Target.min),
        ) {
            Text(stringResource(R.string.plan_edit_discard))
        }
        if (stale) {
            Button(
                onClick = onAskAgain,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Target.min),
            ) {
                Text(stringResource(R.string.plan_edit_ask_again))
            }
        } else {
            Button(
                onClick = onApply,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Target.min),
            ) {
                Text(stringResource(R.string.plan_edit_apply))
            }
        }
    }
}

/** One proposed change, read by TalkBack as one sentence. */
@Composable
private fun ChangeRow(row: PlanEditRow) {
    val (headline, detail, thumbnail) = when (row) {
        is PlanEditRow.Replaced -> Triple(
            stringResource(R.string.plan_edit_row_replaced, row.from, row.to),
            row.after.describe(),
            row.thumbnail,
        )
        is PlanEditRow.Added -> Triple(
            row.afterName
                ?.let { stringResource(R.string.plan_edit_row_added_after, row.name, it) }
                ?: stringResource(R.string.plan_edit_row_added, row.name),
            row.prescription.describe(),
            row.thumbnail,
        )
        is PlanEditRow.Removed -> Triple(
            stringResource(R.string.plan_edit_row_removed, row.name),
            null,
            row.thumbnail,
        )
        is PlanEditRow.Adjusted -> Triple(
            stringResource(R.string.plan_edit_row_adjusted, row.name),
            "${row.before.describe()} → ${row.after.describe()}",
            row.thumbnail,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        ExerciseMedia(mediaRef = thumbnail, contentDescription = null, size = ExerciseMediaSize.SMALL)
        Column(modifier = Modifier.weight(1f)) {
            row.dayNumber?.let {
                Text(
                    stringResource(R.string.plan_edit_row_day, it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(headline, style = MaterialTheme.typography.titleSmall)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Prescription.describe(): String {
    val units = LocalUnitSystem.current
    val target = durationSeconds
        ?.let { stringResource(R.string.plan_edit_sets_seconds, sets, it) }
        ?: stringResource(R.string.plan_edit_sets_reps, sets, reps ?: 0)
    val weight = weightKg?.let { stringResource(R.string.plan_edit_weight, units.formatWeight(it), units.symbol) }
    val rest = stringResource(R.string.plan_edit_rest, restSeconds)
    return listOfNotNull(target, weight, rest).joinToString(" · ")
}
