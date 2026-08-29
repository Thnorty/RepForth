package com.repforth.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.repforth.core.designsystem.component.MuscleSelector
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.exercisedata.labelRes
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.BodyView
import com.repforth.core.model.Muscle

/**
 * Coach's rules-only half (§3, §8).
 *
 * §12 makes Coach a mode inside the builder, not a screen beside it, and this is
 * what that means in practice: it collects one optional answer, hands it to the
 * rules engine, and drops the result into the builder as ordinary editable rows.
 * Nothing is saved on the way through. A generated plan is a starting point, and
 * the user gets the last word on every number in it.
 *
 * Deliberately one question. The profile already knows the goal, the experience,
 * the session length and the equipment; asking again here would be asking
 * someone to repeat themselves, and disagreeing with what they said in
 * onboarding is worse than not asking at all.
 */
@Composable
internal fun CoachScreen(
    state: BuilderUiState,
    onMuscleToggled: (Muscle) -> Unit,
    onRegionToggled: (BodyRegion) -> Unit,
    onGenerate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf(BodyView.FRONT) }
    val defaultName = stringResource(R.string.coach_default_name)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Layout.gutterPhone, vertical = Space.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            Text(
                text = stringResource(R.string.coach_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.heightIn(min = Target.min)) {
                Icon(
                    painter = RfIcons.Close,
                    contentDescription = stringResource(R.string.coach_close),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                horizontal = Layout.gutterPhone,
                vertical = Space.s3,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            item(key = "hint") {
                Text(
                    // Says what empty means, because empty is the useful
                    // default and an untouched body map otherwise reads as an
                    // unanswered question.
                    text = stringResource(R.string.coach_any),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item(key = "map") {
                MuscleSelector(
                    selected = state.coachMuscles,
                    view = view,
                    onViewChange = { view = it },
                    onMuscleToggled = onMuscleToggled,
                    onRegionToggled = onRegionToggled,
                    labelOf = { stringResource(it.labelRes) },
                )
            }

            state.coachFailure?.let { failure ->
                item(key = "failure") {
                    Text(
                        text = stringResource(failure.messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Layout.gutterPhone, vertical = Space.s3),
        ) {
            Button(
                onClick = { onGenerate(defaultName) },
                enabled = !state.generating,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Target.min),
            ) {
                if (state.generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Space.s4),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.coach_generate))
                }
            }
        }
    }
}

/**
 * What to tell someone when nothing could be built.
 *
 * Each names the constraint that did the blocking and where to change it, since
 * every one of these is fixable and none of them is fixable from this screen.
 */
private val CoachFailure.messageRes: Int
    get() = when (this) {
        CoachFailure.NO_PROFILE -> R.string.coach_failed_no_profile
        CoachFailure.EQUIPMENT -> R.string.coach_failed_equipment
        CoachFailure.EXCLUSIONS -> R.string.coach_failed_exclusions
        CoachFailure.MUSCLES -> R.string.coach_failed_muscles
        CoachFailure.NOTHING -> R.string.coach_failed_nothing
    }
