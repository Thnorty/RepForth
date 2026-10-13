package com.repforth.feature.body

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.body.PhotoFailure
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.LocalUnitSystem
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.designsystem.theme.sanitizeWeightInput
import com.repforth.core.model.CheckInProblem
import com.repforth.core.model.Pose
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Whether a camera app can take a photo for us at all. */
@Composable
internal fun rememberCameraAvailable(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
    }
}

/**
 * Record a check-in, or change one.
 *
 * Photos come from the system camera app or the system picker, so the app
 * asks for no permission (§14). [cameraAvailable] is a parameter so a test can
 * say what a phone without a camera app looks like.
 */
@Composable
fun CheckInRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = hiltViewModel(),
    cameraAvailable: Boolean = rememberCameraAvailable(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val units = LocalUnitSystem.current
    var discarding by rememberSaveable { mutableStateOf(false) }
    var capturing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onDone() }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        capturing = false
        viewModel.onCaptureResult(ok)
    }
    var picking by rememberSaveable { mutableStateOf<Pose?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        picking?.let { viewModel.onPhotoPicked(it, uri) }
        picking = null
    }

    BackHandler(enabled = state.dirty && !discarding) { discarding = true }

    CheckInScreen(
        state = state,
        cameraAvailable = cameraAvailable,
        modifier = modifier,
        photoFile = viewModel::photoFile,
        onDateChange = viewModel::onDateChange,
        onFieldChange = viewModel::onFieldChange,
        onTakePhoto = { pose ->
            capturing = true
            try {
                camera.launch(viewModel.newCapture(pose))
            } catch (e: ActivityNotFoundException) {
                capturing = false
                viewModel.onCaptureResult(false)
            }
        },
        onChoosePhoto = { pose ->
            picking = pose
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemovePhoto = viewModel::onRemovePhoto,
        onSave = { viewModel.onSave(units) },
        onAskDelete = viewModel::onAskDelete,
        onConfirmDelete = viewModel::onConfirmDelete,
        onCancelDelete = viewModel::onCancelDelete,
    )

    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text(stringResource(R.string.check_in_discard_title)) },
            text = { Text(stringResource(R.string.check_in_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    discarding = false
                    viewModel.onDiscard()
                    onDone()
                }) { Text(stringResource(R.string.check_in_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.check_in_keep_editing)) }
            },
        )
    }
}

@Composable
internal fun CheckInScreen(
    state: CheckInUiState,
    cameraAvailable: Boolean,
    modifier: Modifier = Modifier,
    photoFile: (String) -> File? = { null },
    onDateChange: (LocalDate) -> Unit = {},
    onFieldChange: (String, String) -> Unit = { _, _ -> },
    onTakePhoto: (Pose) -> Unit = {},
    onChoosePhoto: (Pose) -> Unit = {},
    onRemovePhoto: (Pose) -> Unit = {},
    onSave: () -> Unit = {},
    onAskDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onCancelDelete: () -> Unit = {},
) {
    if (state.loading) return
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Layout.gutterPhone, vertical = Space.s3),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            DateRow(state.date, onClick = { pickingDate = true })

            SectionLabel(stringResource(R.string.check_in_measurements))
            state.fields.forEach { field ->
                MeasurementField(field, unreadable = state.unreadableField == field.type.id, onChange = onFieldChange)
            }

            SectionLabel(stringResource(R.string.body_photos))
            Pose.entries.forEach { pose ->
                PhotoSlot(
                    pose = pose,
                    file = state.photos[pose]?.let(photoFile),
                    filled = pose in state.photos,
                    cameraAvailable = cameraAvailable,
                    onTake = { onTakePhoto(pose) },
                    onChoose = { onChoosePhoto(pose) },
                    onRemove = { onRemovePhoto(pose) },
                )
            }

            state.photoFailure?.let { ErrorText(stringResource(it.messageRes)) }
            state.problem?.let { ErrorText(stringResource(it.messageRes)) }

            if (state.canDelete) {
                TextButton(
                    onClick = onAskDelete,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Target.min),
                ) {
                    Text(stringResource(R.string.check_in_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Layout.gutterPhone, vertical = Space.s3)
                .heightIn(min = Target.min),
        ) {
            Text(stringResource(R.string.check_in_save))
        }
    }

    if (pickingDate) {
        CheckInDatePicker(
            date = state.date,
            today = state.today,
            onPicked = {
                pickingDate = false
                onDateChange(it)
            },
            onDismiss = { pickingDate = false },
        )
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.check_in_delete_title)) },
            text = { Text(stringResource(R.string.check_in_delete_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) { Text(stringResource(R.string.check_in_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.check_in_cancel)) }
            },
        )
    }
}

@Composable
private fun DateRow(date: LocalDate, onClick: () -> Unit) {
    val shown = formatCheckInDate(date)
    val spoken = stringResource(R.string.check_in_date_spoken, shown)
    Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        SectionLabel(stringResource(R.string.check_in_date))
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Target.min)
                .semantics { contentDescription = spoken },
        ) {
            Text(shown)
        }
    }
}

/** A date the way the reader's language writes one. */
@Composable
internal fun formatCheckInDate(date: LocalDate): String =
    date.format(checkInDateFormat(LocalConfiguration.current.locales[0]))

/** The formatter behind [formatCheckInDate], for a caller outside composition. */
internal fun checkInDateFormat(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

/**
 * One measurement. The figure is shown in the reader's units; until it is
 * edited it is the stored value formatted, never something re-read on save.
 */
@Composable
private fun MeasurementField(field: FieldState, unreadable: Boolean, onChange: (String, String) -> Unit) {
    val name = field.type.displayName()
    val shown = field.text ?: field.originalValue?.let { formatMeasurementNumber(it, field.type.kind) }.orEmpty()
    OutlinedTextField(
        value = shown,
        onValueChange = { onChange(field.type.id, sanitizeWeightInput(it)) },
        label = { Text(name) },
        suffix = { Text(field.type.unit()) },
        textStyle = RepForthNumeric.xs,
        singleLine = true,
        isError = unreadable,
        supportingText = if (unreadable) {
            { Text(stringResource(R.string.check_in_not_a_number, name)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PhotoSlot(
    pose: Pose,
    file: File?,
    filled: Boolean,
    cameraAvailable: Boolean,
    onTake: () -> Unit,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    val label = stringResource(pose.labelRes)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
        PhotoThumb(
            file = file,
            contentDescription = if (filled) stringResource(R.string.body_photo_description, label) else null,
            modifier = Modifier.weight(1f),
        )
        Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            PhotoAction(stringResource(R.string.check_in_take_photo), RfIcons.Camera, enabled = cameraAvailable, onClick = onTake)
            if (!cameraAvailable) {
                Text(
                    text = stringResource(R.string.check_in_no_camera),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PhotoAction(stringResource(R.string.check_in_choose_photo), RfIcons.Gallery, enabled = true, onClick = onChoose)
            if (filled) {
                PhotoAction(stringResource(R.string.check_in_remove_photo), RfIcons.Delete, enabled = true, onClick = onRemove)
            }
        }
    }
}

@Composable
private fun PhotoAction(
    text: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = Target.min),
    ) {
        Icon(icon, contentDescription = null)
        Text(text, modifier = Modifier.padding(start = Space.s2))
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/** No day after [today]: a check-in records what was, not what is planned. */
@OptIn(ExperimentalMaterial3Api::class)
internal class NotInFuture(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)

    override fun isSelectableYear(year: Int): Boolean = year <= today.year
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInDatePicker(
    date: LocalDate,
    today: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = remember(today) { NotInFuture(today) },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let {
                    onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_in_cancel)) }
        },
    ) {
        DatePicker(state = picker)
    }
}

private val CheckInProblem.messageRes: Int
    get() = when (this) {
        CheckInProblem.EMPTY -> R.string.check_in_problem_empty
        CheckInProblem.FUTURE_DATE -> R.string.check_in_problem_future
        CheckInProblem.NOT_POSITIVE -> R.string.check_in_problem_not_positive
        CheckInProblem.PERCENT_OVER_100 -> R.string.check_in_problem_percent
        CheckInProblem.UNKNOWN_TYPE -> R.string.check_in_problem_unknown_type
        CheckInProblem.DATE_TAKEN -> R.string.check_in_problem_date_taken
    }

private val PhotoFailure.messageRes: Int
    get() = when (this) {
        PhotoFailure.UNREADABLE -> R.string.check_in_photo_unreadable
        PhotoFailure.WRITE_FAILED -> R.string.check_in_photo_write_failed
    }
