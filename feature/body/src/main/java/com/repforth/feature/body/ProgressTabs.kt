package com.repforth.feature.body

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.repforth.core.designsystem.component.RfSegmentedChoice
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space

private enum class ProgressTab { WORKOUTS, BODY }

/**
 * The Progress tab's two halves, Workouts and Body, behind one switch.
 *
 * Slots rather than routes, so this module need not know the workout history
 * exists. The choice survives leaving the tab and coming back.
 */
@Composable
fun ProgressTabs(
    workouts: @Composable () -> Unit,
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(ProgressTab.WORKOUTS) }
    Column(modifier = modifier.fillMaxSize()) {
        RfSegmentedChoice(
            options = ProgressTab.entries,
            selected = selected,
            label = { tab ->
                when (tab) {
                    ProgressTab.WORKOUTS -> stringResource(R.string.body_tab_workouts)
                    ProgressTab.BODY -> stringResource(R.string.body_tab_body)
                }
            },
            onSelect = { selected = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Layout.gutterPhone, vertical = Space.s2),
        )
        Box(modifier = Modifier.weight(1f)) {
            when (selected) {
                ProgressTab.WORKOUTS -> workouts()
                ProgressTab.BODY -> body()
            }
        }
    }
}
