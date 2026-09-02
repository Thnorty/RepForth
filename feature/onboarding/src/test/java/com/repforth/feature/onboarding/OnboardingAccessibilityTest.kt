package com.repforth.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.TrainingGoal
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
 * What TalkBack would find during onboarding.
 *
 * The steps are built from choice cards and a slider, which is the shape most
 * likely to reach a screen reader as an unnamed row with a value nobody said
 * out loud.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class OnboardingAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun goal_english() = check(ENGLISH, goal())

    @Test
    fun goal_turkish() = check(TURKISH, goal())

    @Test
    fun experience_english() = check(ENGLISH, experience())

    /** The steps built from chip rows, which is where touch targets go small. */
    @Test
    fun equipment_english() = check(ENGLISH, equipment())

    @Test
    fun equipment_turkish() = check(TURKISH, equipment())

    @Test
    fun muscles_english() = check(ENGLISH, muscles())

    @Test
    fun days_english() = check(ENGLISH, days())

    @Test
    fun days_turkish() = check(TURKISH, days())

    private fun check(locale: String, state: OnboardingUiState) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)

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

        compose.assertScreenIsAccessible("Onboarding ${state.step} ($locale)")
    }

    private fun goal() = OnboardingUiState(
        step = OnboardingStep.GOAL,
        goal = TrainingGoal.GENERAL_FITNESS,
    )

    private fun experience() = OnboardingUiState(
        step = OnboardingStep.EXPERIENCE,
        goal = TrainingGoal.GENERAL_FITNESS,
        experience = ExperienceLevel.ADVANCED,
    )

    private fun equipment() = OnboardingUiState(
        step = OnboardingStep.EQUIPMENT,
        goal = TrainingGoal.GENERAL_FITNESS,
        experience = ExperienceLevel.ADVANCED,
        equipment = setOf(Equipment.BODY_WEIGHT, Equipment.BARBELL),
    )

    private fun muscles() = OnboardingUiState(
        step = OnboardingStep.MUSCLES,
        goal = TrainingGoal.GENERAL_FITNESS,
        experience = ExperienceLevel.ADVANCED,
        equipment = setOf(Equipment.BODY_WEIGHT, Equipment.BARBELL),
    )

    private fun days() = OnboardingUiState(
        step = OnboardingStep.DAYS,
        goal = TrainingGoal.GENERAL_FITNESS,
        experience = ExperienceLevel.ADVANCED,
        equipment = setOf(Equipment.BODY_WEIGHT, Equipment.BARBELL),
        trainingDaysPerWeek = 6,
    )
}
