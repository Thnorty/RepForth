package com.repforth.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.repforth.core.designsystem.theme.Target

/**
 * One of a few peers, side by side: Workouts | Body, Front | Side | Back.
 *
 * Material's segmented row, held to the 48dp target. Labels wrap rather than
 * truncate, so a long Turkish word at 2× still says what it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> RfSegmentedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.heightIn(min = Target.min),
                label = { Text(label(option), overflow = TextOverflow.Visible) },
            )
        }
    }
}
