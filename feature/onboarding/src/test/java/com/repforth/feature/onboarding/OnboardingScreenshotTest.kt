package com.repforth.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.TrainingGoal
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_COMPARISON
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.screenshotPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The seven questions, at the steps most likely to overflow.
 *
 * Two defects on this screen were found on a device and by nothing else: it
 * drew under the camera cutout, and day six of its slider could not be
 * selected. The second has a unit test now; the first is a layout, which is
 * what this is for.
 *
 * Goal and experience are the interesting steps because each option carries an
 * explanatory line under its label, and the length of both changes with the
 * language.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class OnboardingScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun goal_english() = capture("onboarding-goal-en", goal())

    @Test
    fun goal_turkish() = capture("onboarding-goal-tr", goal(), locale = TURKISH)

    @Test
    fun goal_large_text() = capture("onboarding-goal-en-2x", goal(), fontScale = 2f)

    @Test
    fun goal_turkish_large_text() =
        capture("onboarding-goal-tr-2x", goal(), locale = TURKISH, fontScale = 2f)

    /** The years moved to a detail line here when the labels became levels. */
    @Test
    fun experience_english() = capture("onboarding-experience-en", experience())

    @Test
    fun experience_turkish() =
        capture("onboarding-experience-tr", experience(), locale = TURKISH)

    /** The step whose sixth value was once unreachable. */
    @Test
    fun days_english() = capture("onboarding-days-en", days())

    @Test
    fun days_turkish_large_text() =
        capture("onboarding-days-tr-2x", days(), locale = TURKISH, fontScale = 2f)

    private fun capture(
        name: String,
        state: OnboardingUiState,
        locale: String? = null,
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setQualifiers("+${locale ?: ENGLISH}")
        RuntimeEnvironment.setFontScale(fontScale)

        compose.setContent {
            RepForthPreviewHost {
                OnboardingScreen(
                    state = state,
                    onGoalSelected = {},
                    onExperienceSelected = {},
                    onEquipmentToggled = {},
                    onDaysChanged = {},
                    onSessionLengthChanged = {},
                    onPreferredMuscleToggled = {},
                    onPreferredRegionToggled = {},
                    onAvoidedMuscleToggled = {},
                    onAvoidedRegionToggled = {},
                    onJumpTo = {},
                    onBack = {},
                    onNext = {},
                    onFinish = {},
                )
            }
        }

        compose.onRoot().captureRoboImage(screenshotPath(name), SCREENSHOT_COMPARISON)
    }

    private fun goal() = OnboardingUiState(
        step = OnboardingStep.GOAL,
        // The longest of the four, so the picture shows the case that wraps.
        goal = TrainingGoal.GENERAL_FITNESS,
    )

    private fun experience() = OnboardingUiState(
        step = OnboardingStep.EXPERIENCE,
        goal = TrainingGoal.GENERAL_FITNESS,
        experience = ExperienceLevel.ADVANCED,
    )

    private fun days() = OnboardingUiState(
        step = OnboardingStep.DAYS,
        goal = TrainingGoal.GENERAL_FITNESS,
        experience = ExperienceLevel.ADVANCED,
        equipment = setOf(Equipment.BODY_WEIGHT, Equipment.BARBELL),
        trainingDaysPerWeek = 6,
    )
}
