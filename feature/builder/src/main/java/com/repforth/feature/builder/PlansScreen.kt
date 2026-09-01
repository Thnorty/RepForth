package com.repforth.feature.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.WorkoutTemplate

/**
 * The saved plan library (§12, §6 `docs/WEEKLY_PLANS.md`).
 *
 * §12 makes this the entry point for creating a workout, which is why the
 * builder has no tab of its own: a plan is something you keep and come back to,
 * and the act of making one belongs next to the ones already made.
 */
@Composable
fun PlansRoute(
    onNewWorkout: () -> Unit,
    onEditPlan: (String) -> Unit,
    onStartPlan: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val weeklyPlans by viewModel.weeklyPlans.collectAsStateWithLifecycle()

    PlansScreen(
        plans = plans,
        weeklyPlans = weeklyPlans,
        onNewWorkout = onNewWorkout,
        onEditPlan = onEditPlan,
        onStartPlan = onStartPlan,
        onDelete = viewModel::onDelete,
        onDeleteWeek = viewModel::onDeleteWeek,
        onSetActiveWeek = viewModel::onSetActiveWeek,
        modifier = modifier,
    )
}

@Composable
internal fun PlansScreen(
    plans: List<WorkoutTemplate>,
    weeklyPlans: List<TrainingWeek> = emptyList(),
    onNewWorkout: () -> Unit,
    onEditPlan: (String) -> Unit,
    onStartPlan: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteWeek: (String) -> Unit = {},
    onSetActiveWeek: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Deleting a week cascades to every workout inside it, which is the chosen
    // behaviour and precisely why it is worth a question first: one mistap on an
    // icon otherwise removes five workouts with no undo. The dialog names the
    // count, because "Delete this week?" does not describe what is about to
    // happen.
    var weekPendingDeletion by rememberSaveable { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (plans.isEmpty() && weeklyPlans.isEmpty()) {
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
                bottom = Layout.fabSize + Space.s8,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            if (weeklyPlans.isNotEmpty()) {
                item(key = "weekly_header") {
                    Text(
                        text = stringResource(R.string.plans_weekly_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = Space.s1),
                    )
                }
                items(weeklyPlans, key = { it.id }) { week ->
                    WeeklyPlanCard(
                        week = week,
                        onStartDay = onStartPlan,
                        onEditDay = onEditPlan,
                        onDelete = { weekPendingDeletion = week.id },
                        onSetActive = { onSetActiveWeek(week.id) },
                    )
                }
            }

            if (plans.isNotEmpty()) {
                if (weeklyPlans.isNotEmpty()) {
                    item(key = "workouts_header") {
                        Text(
                            text = stringResource(R.string.plans_workouts_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Space.s3, bottom = Space.s1),
                        )
                    }
                }
                items(plans, key = { it.id }) { plan ->
                    PlanCard(
                        plan = plan,
                        onClick = { onEditPlan(plan.id) },
                        onStart = { onStartPlan(plan.id) },
                        onDelete = { onDelete(plan.id) },
                    )
                }
            }
        }

        val newWorkout = stringResource(R.string.plans_new)
        ExtendedFloatingActionButton(
            onClick = onNewWorkout,
            icon = { Icon(painter = RfIcons.Add, contentDescription = null) },
            text = { Text(newWorkout) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Layout.gutterPhone)
                .semantics { contentDescription = newWorkout },
        )
    }

    weekPendingDeletion?.let { pendingId ->
        val week = weeklyPlans.firstOrNull { it.id == pendingId }
        if (week == null) {
            // The week disappeared underneath the dialog — another delete, an
            // import, a reset. Close rather than ask about something gone.
            weekPendingDeletion = null
            return@let
        }
        AlertDialog(
            onDismissRequest = { weekPendingDeletion = null },
            title = { Text(stringResource(R.string.plans_delete_week_title, week.name)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.plans_delete_week_body,
                        week.days.size,
                        week.days.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        weekPendingDeletion = null
                        onDeleteWeek(pendingId)
                    },
                ) {
                    Text(stringResource(R.string.plans_delete_week_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { weekPendingDeletion = null }) {
                    Text(stringResource(R.string.plans_delete_week_dismiss))
                }
            },
        )
    }
}

@Composable
private fun WeeklyPlanCard(
    week: TrainingWeek,
    onStartDay: (String) -> Unit,
    onEditDay: (String) -> Unit,
    onDelete: () -> Unit,
    onSetActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(week.active) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.s1),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = week.name, style = MaterialTheme.typography.titleMedium)
                        if (week.active) {
                            Text(
                                text = stringResource(R.string.plans_active_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.week_summary_days, week.days.size) +
                            " · " +
                            stringResource(R.string.week_summary_duration, (week.estimatedDurationMs / 60_000L).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.heightIn(min = Target.min),
                ) {
                    Icon(
                        painter = if (expanded) RfIcons.Collapse else RfIcons.Expand,
                        contentDescription = null,
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.heightIn(min = Target.min),
                ) {
                    Icon(
                        painter = RfIcons.Delete,
                        contentDescription = stringResource(R.string.plans_delete, week.name),
                    )
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = Space.s2),
                    verticalArrangement = Arrangement.spacedBy(Space.s2),
                ) {
                    week.days.forEach { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.s1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // A day of a week opens the same way a standalone
                            // plan does: tapping the row edits it. It was the
                            // only row in the app that looked like a plan and
                            // did nothing, and the Start button beside it made
                            // that read as deliberate rather than missing.
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onEditDay(day.workout.id) }
                                    .heightIn(min = Target.min)
                                    .padding(vertical = Space.s1),
                            ) {
                                Text(
                                    text = weekDayLabel(day.position, day.title),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.plans_summary,
                                        day.workout.exercises.size,
                                        day.workout.exercises.size,
                                        (day.workout.estimatedDurationMs / 60_000L).toInt(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = { onStartDay(day.workout.id) },
                                modifier = Modifier.heightIn(min = Target.min),
                            ) {
                                Text(stringResource(R.string.plans_start_short))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: WorkoutTemplate,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
) {
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
                    text = pluralStringResource(
                        R.plurals.plans_summary,
                        plan.exercises.size,
                        plan.exercises.size,
                        (plan.estimatedDurationMs / 60_000L).toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onStart,
                modifier = Modifier.heightIn(min = Target.min),
            ) {
                Text(stringResource(R.string.plans_start_short))
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
