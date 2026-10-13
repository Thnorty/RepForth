package com.repforth.feature.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import java.io.File

/** Every check-in, newest first; tapping one opens it for editing. */
@Composable
fun CheckInListRoute(
    onOpenCheckIn: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckInListViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    rows?.let { CheckInListScreen(it, modifier, viewModel::photoFile, onOpenCheckIn) }
}

@Composable
internal fun CheckInListScreen(
    rows: List<CheckInRow>,
    modifier: Modifier = Modifier,
    photoFile: (String) -> File? = { null },
    onOpenCheckIn: (String) -> Unit = {},
) {
    if (rows.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(Layout.gutterPhone), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.check_ins_empty),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Layout.gutterPhone, vertical = Space.s3),
    ) {
        items(rows, key = { it.id }) { row ->
            CheckInListRow(row, photoFile, onClick = { onOpenCheckIn(row.id) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun CheckInListRow(row: CheckInRow, photoFile: (String) -> File?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.min)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        // Decorative: the row's text says what it is, and a photo has no words.
        // A row without one keeps the slot, so every date starts in one column.
        if (row.thumbnail != null) {
            PhotoThumb(
                file = photoFile(row.thumbnail),
                contentDescription = null,
                modifier = Modifier.width(Layout.photoThumbSmall),
            )
        } else {
            Spacer(Modifier.width(Layout.photoThumbSmall))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            Text(formatCheckInDate(row.date), style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (row.measurementCount == 0) {
                    stringResource(R.string.check_ins_photos_only)
                } else {
                    pluralStringResource(R.plurals.check_ins_measurements, row.measurementCount, row.measurementCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
