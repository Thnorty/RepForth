package com.repforth.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.repforth.core.designsystem.component.RfChoiceChips
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target

/*
 * The rows both settings screens are built from.
 *
 * Extracted when the AI provider screen needed every one of them. A second copy
 * would have been a second set of paddings, a second minimum touch target, and
 * two screens that drift apart while looking identical in a review.
 */

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Space.s4, bottom = Space.s1),
    )
}

/** The screen's own label above the shared chip control. */
@Composable
internal fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    RfChoiceChips(
        options = options,
        selected = selected,
        labelOf = labelOf,
        onSelected = onSelected,
        label = label,
    )
}

/**
 * A label, an explanation, and a switch — tappable anywhere along the row.
 *
 * `toggleable` on the Row rather than a listener on the Switch alone. Found on
 * a device: tapping the words did nothing, so the only target was the switch
 * itself, about 15% of a row the user has every reason to read as one control.
 * It also merges the row into a single node for accessibility, so TalkBack
 * announces the label, the explanation and the state together instead of
 * offering an unnamed switch after them.
 */
@Composable
internal fun SwitchRow(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.min)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            )
            .padding(vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Null: the row owns the click, and a switch with its own handler
        // would take the tap back and double-report the state change.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun ActionRow(
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.min)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            // Colour is not the only signal: every destructive action here also
            // asks a question before doing anything.
            color = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun DestructiveDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * Whether to suggest exporting first.
     *
     * False where an export would not help. Deleting a provider key destroys
     * something an export has never contained and never will (§20), so offering
     * a backup there would be advice that quietly does not work.
     */
    offerExport: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(body)
                if (offerExport) {
                    // Offered at the moment it is useful rather than as a
                    // warning nobody reads: this is the last screen before the
                    // data is gone.
                    Text(
                        text = stringResource(R.string.settings_export_first),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

