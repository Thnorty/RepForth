package com.repforth.feature.builder

import com.repforth.core.designsystem.theme.Target
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.model.WorkoutTemplate

/**
 * The saved plan library (§12).
 *
 * §12 makes this the entry point for creating a workout, which is why the
 * builder has no tab of its own: a plan is something you keep and come back to,
 * and the act of making one belongs next to the ones already made.
 */
@Composable
fun PlansRoute(
    onNewWorkout: () -> Unit,
    onEditPlan: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()

    PlansScreen(
        plans = plans,
        onNewWorkout = onNewWorkout,
        onEditPlan = onEditPlan,
        onDelete = viewModel::onDelete,
        modifier = modifier,
    )
}

@Composable
internal fun PlansScreen(
    plans: List<WorkoutTemplate>,
    onNewWorkout: () -> Unit,
    onEditPlan: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (plans.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Layout.gutterPhone),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.plans_empty),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.plans_empty_action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Layout.gutterPhone,
                end = Layout.gutterPhone,
                top = Space.s3,
                // Clear of the FAB, which would otherwise sit on top of the
                // last plan's delete button.
                bottom = Layout.fabSize + Space.s8,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            items(plans, key = { it.id }) { plan ->
                PlanCard(
                    plan = plan,
                    onClick = { onEditPlan(plan.id) },
                    onDelete = { onDelete(plan.id) },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNewWorkout,
            icon = { Icon(painter = RfIcons.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.plans_new)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Layout.gutterPhone),
        )
    }
}

@Composable
private fun PlanCard(plan: WorkoutTemplate, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(Space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.s1),
            ) {
                Text(text = plan.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.plans_summary,
                        plan.exercises.size,
                        (plan.estimatedDurationMs / 60_000L).toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.heightIn(min = Target.min),
            ) {
                Icon(
                    painter = RfIcons.Delete,
                    contentDescription = stringResource(R.string.plans_delete, plan.name),
                )
            }
        }
    }
}
