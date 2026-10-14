package com.repforth.feature.body

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.component.RfSegmentedChoice
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.LocalUnitSystem
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.designsystem.theme.symbol
import com.repforth.core.model.MeasurementKind
import com.repforth.core.model.Pose
import java.io.File
import java.time.LocalDate

/** Two photos of one pose, side by side, from two days the person chooses. */
@Composable
fun ComparePhotosRoute(
    modifier: Modifier = Modifier,
    viewModel: ComparePhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state?.let {
        ComparePhotosScreen(
            state = it,
            modifier = modifier,
            photoFile = viewModel::photoFile,
            onPose = viewModel::onPose,
            onLeftDate = viewModel::onLeftDate,
            onRightDate = viewModel::onRightDate,
        )
    }
}

@Composable
internal fun ComparePhotosScreen(
    state: ComparePhotosUiState,
    modifier: Modifier = Modifier,
    photoFile: (String) -> File? = { null },
    onPose: (Pose) -> Unit = {},
    onLeftDate: (LocalDate) -> Unit = {},
    onRightDate: (LocalDate) -> Unit = {},
) {
    // The file name, not the File: a name survives being saved across rotation.
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val poseLabel = stringResource(state.pose.labelRes)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.gutterPhone, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        RfSegmentedChoice(
            options = Pose.entries,
            selected = state.pose,
            label = { stringResource(it.labelRes) },
            onSelect = onPose,
        )
        val left = state.left
        val right = state.right
        if (left == null || right == null) {
            Text(
                text = stringResource(R.string.compare_needs_two),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Space.s6),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                listOf(left to onLeftDate, right to onRightDate).forEach { (side, onDate) ->
                    CompareColumn(
                        side = side,
                        dates = state.dates,
                        poseLabel = poseLabel,
                        photoFile = photoFile,
                        onDate = onDate,
                        onOpen = { opened = side.fileName },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // Looked up rather than kept: a pose switch can take the opened photo away.
    listOfNotNull(state.left, state.right).firstOrNull { it.fileName == opened }?.let { side ->
        FullScreenPhoto(
            file = photoFile(side.fileName),
            description = stringResource(R.string.compare_photo_description, poseLabel, formatCheckInDate(side.date)),
            onClose = { opened = null },
        )
    }
}

@Composable
private fun CompareColumn(
    side: CompareSide,
    dates: List<LocalDate>,
    poseLabel: String,
    photoFile: (String) -> File?,
    onDate: (LocalDate) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.compare_photo_description, poseLabel, formatCheckInDate(side.date))
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        DateMenu(selected = side.date, dates = dates, onDate = onDate)
        // The words sit on the clickable frame rather than on the image, so the
        // photo is named even while it loads, or if its file has gone.
        PhotoThumb(
            file = photoFile(side.fileName),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description }
                .clickable(role = Role.Button, onClick = onOpen),
        )
        side.weightKg?.let { kg ->
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
                Text(formatMeasurementNumber(kg, MeasurementKind.MASS), style = RepForthNumeric.xs)
                Text(
                    text = LocalUnitSystem.current.symbol,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The side's day; tapping it lists every day holding this pose. */
@Composable
private fun DateMenu(selected: LocalDate, dates: List<LocalDate>, onDate: (LocalDate) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = Target.min),
        ) {
            Text(formatCheckInDate(selected), modifier = Modifier.weight(1f))
            Icon(RfIcons.Expand, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            dates.asReversed().forEach { date ->
                DropdownMenuItem(
                    text = { Text(formatCheckInDate(date)) },
                    onClick = {
                        expanded = false
                        onDate(date)
                    },
                )
            }
        }
    }
}

/** One photo, fitted whole to the screen, with a way back. */
@Composable
private fun FullScreenPhoto(file: File?, description: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(Space.s2),
            ) {
                Icon(RfIcons.Close, contentDescription = stringResource(R.string.compare_close))
            }
        }
    }
}
