package com.repforth.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
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
 * What TalkBack would find on Settings.
 *
 * Both languages, because a `contentDescription` hardcoded in English is
 * invisible to a screenshot and to every other test in this repo -- the picture
 * looks identical whichever language the description was written in.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class SettingsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settings_english() = check(ENGLISH)

    @Test
    fun settings_turkish() = check(TURKISH)

    private fun check(locale: String) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)

        compose.setContent {
            RepForthPreviewHost {
                SettingsScreen(
                    state = SettingsUiState(profile = SAMPLE_PROFILE),
                    onGoalChange = {},
                    onExperienceChange = {},
                    onScheduleChange = { _, _ -> },
                    onEquipmentChange = {},
                    onThemeChange = {},
                    onLanguageChange = {},
                    onUnitsChange = {},
                    onKeepScreenOnChange = {},
                    onHapticsChange = {},
                    onReducedMotionChange = {},
                    onMediaWifiOnlyChange = {},
                    onClearMediaCache = {},
                    onOpenAiSettings = {},
                    onExport = {},
                    onImport = {},
                    onImportConfirmed = {},
                    onImportCancelled = {},
                    onDeleteWorkoutData = {},
                    onResetApp = {},
                    onMessageShown = {},
                )
            }
        }

        compose.assertScreenIsAccessible("Settings ($locale)")
    }

    private companion object {
        val SAMPLE_PROFILE = UserProfile(
            id = "a11y",
            goal = TrainingGoal.GENERAL_FITNESS,
            experience = ExperienceLevel.ADVANCED,
            trainingDaysPerWeek = 4,
            sessionLengthMs = 45 * 60_000L,
            availableEquipment = setOf(Equipment.BODY_WEIGHT, Equipment.BARBELL),
            preferredMuscles = emptySet(),
            exclusions = emptySet(),
        )
    }
}
