package com.repforth.feature.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.component.RfChoiceRows
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.model.MeasurementKind
import com.repforth.core.model.MeasurementType

/** Which measurements a check-in asks for, and the user's own. */
@Composable
fun TrackingRoute(
    modifier: Modifier = Modifier,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state?.let {
        TrackingScreen(
            state = it,
            modifier = modifier,
            onToggle = viewModel::onToggle,
            onAdd = viewModel::onAdd,
            onRename = viewModel::onRename,
            onAskDelete = viewModel::onAskDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onCancelDelete = viewModel::onCancelDelete,
        )
    }
}

@Composable
internal fun TrackingScreen(
    state: TrackingUiState,
    modifier: Modifier = Modifier,
    onToggle: (String, Boolean) -> Unit = { _, _ -> },
    onAdd: (String, MeasurementKind, String?) -> CustomProblem? = { _, _, _ -> null },
    onRename: (String, String) -> CustomProblem? = { _, _ -> null },
    onAskDelete: (String) -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onCancelDelete: () -> Unit = {},
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    val builtIns = state.types.filter { it.builtIn != null }
    val customs = state.types.filter { it.builtIn == null }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Layout.gutterPhone, vertical = Space.s3),
        ) {
            item(key = "built-in") { SectionLabel(stringResource(R.string.tracking_built_in)) }
            items(builtIns, key = { it.id }) { type ->
                SwitchRow(type, onToggle)
            }
            if (customs.isNotEmpty()) {
                item(key = "custom") {
                    SectionLabel(stringResource(R.string.tracking_custom), Modifier.padding(top = Space.s4))
                }
                items(customs, key = { it.id }) { type ->
                    CustomRow(type, onToggle, onRename = { renaming = type.id }, onDelete = { onAskDelete(type.id) })
                }
            }
        }
        Button(
            onClick = { adding = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Layout.gutterPhone, vertical = Space.s3)
                .heightIn(min = Target.min),
        ) {
            Text(stringResource(R.string.tracking_add))
        }
    }

    if (adding) {
        AddCustomDialog(onAdd = onAdd, onDone = { adding = false })
    }
    customs.firstOrNull { it.id == renaming }?.let { type ->
        RenameDialog(type, onRename = { onRename(type.id, it) }, onDone = { renaming = null })
    }
    state.pendingDelete?.let { DeleteDialog(it, onConfirmDelete, onCancelDelete) }
}

/** The whole row is the switch, so the target is the row and TalkBack hears one control. */
@Composable
private fun SwitchRow(type: MeasurementType, onToggle: (String, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val unit = type.unit()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Target.min)
            .toggleable(value = type.tracked, onValueChange = { onToggle(type.id, it) }, role = Role.Switch)
            .padding(vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(type.displayName(), style = MaterialTheme.typography.bodyLarge)
            if (type.builtIn == null && unit.isNotEmpty()) {
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Null: the row owns the click, and a switch with its own handler
        // would take the tap back and double-report the state change.
        Switch(checked = type.tracked, onCheckedChange = null)
    }
}

@Composable
private fun CustomRow(
    type: MeasurementType,
    onToggle: (String, Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val name = type.displayName()
    Row(verticalAlignment = Alignment.CenterVertically) {
        SwitchRow(type, onToggle, Modifier.weight(1f))
        IconButton(onClick = onRename) {
            Icon(RfIcons.Edit, contentDescription = stringResource(R.string.tracking_rename_action, name))
        }
        IconButton(onClick = onDelete) {
            Icon(RfIcons.Delete, contentDescription = stringResource(R.string.tracking_delete_action, name))
        }
    }
}

@Composable
private fun AddCustomDialog(
    onAdd: (String, MeasurementKind, String?) -> CustomProblem?,
    onDone: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(MeasurementKind.LENGTH) }
    var unit by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf<CustomProblem?>(null) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.tracking_add_title)) },
        text = {
            AddCustomForm(
                name = name,
                onName = { name = it; problem = null },
                kind = kind,
                onKind = { kind = it; problem = null },
                unit = unit,
                onUnit = { unit = it; problem = null },
                problem = problem,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                problem = onAdd(name, kind, unit.takeIf { kind == MeasurementKind.PLAIN })
                if (problem == null) onDone()
            }) {
                Text(stringResource(R.string.tracking_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.tracking_cancel)) }
        },
    )
}

/** The add dialog's fields, apart from the dialog so a test can host them in the main window. */
@Composable
internal fun AddCustomForm(
    name: String,
    onName: (String) -> Unit,
    kind: MeasurementKind,
    onKind: (MeasurementKind) -> Unit,
    unit: String,
    onUnit: (String) -> Unit,
    problem: CustomProblem?,
) {
    // Scrolls: at 2x in Turkish the four kinds and two fields outgrow a phone.
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        NameField(name, problem, onName)
        Text(stringResource(R.string.tracking_kind), style = MaterialTheme.typography.labelLarge)
        RfChoiceRows(
            options = KIND_ORDER,
            selected = kind,
            labelOf = { stringResource(it.labelRes) },
            onSelected = onKind,
        )
        if (kind == MeasurementKind.PLAIN) {
            val unitProblem = problem?.takeIf { it == CustomProblem.UNIT_MISSING || it == CustomProblem.UNIT_TOO_LONG }
            OutlinedTextField(
                value = unit,
                onValueChange = onUnit,
                label = { Text(stringResource(R.string.tracking_unit)) },
                singleLine = true,
                isError = unitProblem != null,
                supportingText = unitProblem?.let { { Text(stringResource(it.messageRes)) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RenameDialog(type: MeasurementType, onRename: (String) -> CustomProblem?, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(type.customName.orEmpty()) }
    var problem by rememberSaveable { mutableStateOf<CustomProblem?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.tracking_rename_title)) },
        text = { NameField(name, problem) { name = it; problem = null } },
        confirmButton = {
            TextButton(onClick = {
                problem = onRename(name)
                if (problem == null) onDone()
            }) {
                Text(stringResource(R.string.tracking_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.tracking_cancel)) }
        },
    )
}

@Composable
private fun NameField(name: String, problem: CustomProblem?, onChange: (String) -> Unit) {
    val nameProblem = problem?.takeIf { it == CustomProblem.NAME_BLANK || it == CustomProblem.NAME_TOO_LONG }
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.tracking_name)) },
        singleLine = true,
        isError = nameProblem != null,
        supportingText = nameProblem?.let { { Text(stringResource(it.messageRes)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeleteDialog(pending: PendingDelete, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val name = pending.type.displayName()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.tracking_delete_title)) },
        text = {
            Text(
                if (pending.entries == 0) {
                    stringResource(R.string.tracking_delete_message_empty, name)
                } else {
                    pluralStringResource(R.plurals.tracking_delete_message, pending.entries, name, pending.entries)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.tracking_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.tracking_cancel)) }
        },
    )
}

private val MeasurementKind.labelRes: Int
    get() = when (this) {
        MeasurementKind.LENGTH -> R.string.tracking_kind_length
        MeasurementKind.MASS -> R.string.tracking_kind_mass
        MeasurementKind.PERCENT -> R.string.tracking_kind_percent
        MeasurementKind.PLAIN -> R.string.tracking_kind_plain
    }

private val CustomProblem.messageRes: Int
    get() = when (this) {
        CustomProblem.NAME_BLANK -> R.string.tracking_name_blank
        CustomProblem.NAME_TOO_LONG -> R.string.tracking_name_too_long
        CustomProblem.UNIT_MISSING -> R.string.tracking_unit_missing
        CustomProblem.UNIT_TOO_LONG -> R.string.tracking_unit_too_long
    }

/** Length first: it is what most people add — a skinfold, a wrist. */
private val KIND_ORDER = listOf(MeasurementKind.LENGTH, MeasurementKind.MASS, MeasurementKind.PERCENT, MeasurementKind.PLAIN)
