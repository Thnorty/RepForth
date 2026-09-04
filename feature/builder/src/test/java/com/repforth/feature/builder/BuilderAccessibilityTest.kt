package com.repforth.feature.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.assertScreenIsAccessible
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What TalkBack would find on the four screens a plan is built through.
 *
 * Plans and week review are the two that carry expandable headers, and both
 * reached this suite with an unnamed chevron and no announced open/closed
 * state — a section a screen-reader user could neither identify nor tell the
 * state of. Neither was visible to a golden: the picture is identical either
 * way.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class BuilderAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun plans_english() = check(ENGLISH, "Plans") { PlansContent() }

    @Test
    fun plans_turkish() = check(TURKISH, "Plans") { PlansContent() }

    @Test
    fun builder_english() = check(ENGLISH, "Builder") { BuilderContent() }

    @Test
    fun coach_english() = check(ENGLISH, "Coach") { CoachContent() }

    @Test
    fun coach_turkish() = check(TURKISH, "Coach") { CoachContent() }

    @Test
    fun week_review_english() = check(ENGLISH, "Week review") { WeekReviewContent() }

    @Test
    fun week_review_turkish() = check(TURKISH, "Week review") { WeekReviewContent() }

    private fun check(locale: String, screen: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent { RepForthPreviewHost { content() } }
        compose.assertScreenIsAccessible("$screen ($locale)")
    }

    @Composable
    private fun PlansContent() {
        PlansScreen(
            plans = listOf(BuilderFixtures.STANDALONE),
            weeklyPlans = listOf(BuilderFixtures.WEEK),
            onNewWorkout = {},
            onEditPlan = {},
            onEditWeek = {},
            onStartPlan = {},
            onDelete = {},
        )
    }

    @Composable
    private fun BuilderContent() {
        BuilderScreen(
            state = BuilderFixtures.WITH_ROWS,
            onNameChange = {},
            onAddExercise = {},
            onOpenExerciseDetail = {},
            onCoach = {},
            onRemove = {},
            onMove = { _, _ -> },
            onSetsChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onDurationChange = { _, _ -> },
            onRestChange = { _, _ -> },
            onWeightChange = { _, _ -> },
            onTimedChange = { _, _ -> },
            onSave = {},
        )
    }

    @Composable
    private fun CoachContent() {
        CoachScreen(
            state = BuilderFixtures.COACH,
            onMuscleToggled = {},
            onRegionToggled = {},
            onGenerate = {},
            onDaysChange = {},
            onGoalChange = {},
            onExperienceChange = {},
            onSessionMinutesChange = {},
            onSaveDefaults = {},
            onCancelGenerate = {},
            onDismissError = {},
            onClose = {},
            onOpenProviderSettings = {},
        )
    }

    @Composable
    private fun WeekReviewContent() {
        WeekReviewScreen(
            state = BuilderFixtures.WEEK_DRAFT,
            onWeekNameChange = {},
            onDayTitleChange = { _, _ -> },
            onToggleDayExpanded = {},
            onAddExerciseToDay = {},
            onOpenExerciseDetail = {},
            onRemoveExerciseFromDay = { _, _ -> },
            onMoveExerciseInDay = { _, _, _ -> },
            onSetsChangeInDay = { _, _, _ -> },
            onRepsChangeInDay = { _, _, _ -> },
            onDurationChangeInDay = { _, _, _ -> },
            onRestChangeInDay = { _, _, _ -> },
            onWeightChangeInDay = { _, _, _ -> },
            onTimedChangeInDay = { _, _, _ -> },
            onSaveWeek = {},
            onCoach = {},
        )
    }
}
