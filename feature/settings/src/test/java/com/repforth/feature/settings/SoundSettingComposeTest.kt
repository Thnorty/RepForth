package com.repforth.feature.settings

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.UserPreferences
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sound switch is on screen, and turning it off says so.
 *
 * Written because nothing else would notice if it were not. Settings is a
 * `LazyColumn` and this row sits below the fold, so the screenshot goldens were
 * unchanged by adding it and the accessibility walk never composed it — a row
 * that rendered nothing, or that was wired to the wrong callback, would have
 * passed the whole suite.
 *
 * That is the same shape as the defect `PreferenceReachTest` exists for, one
 * step earlier: that test proves something downstream *reads* the preference,
 * and this one proves the user has a way to change it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class SoundSettingComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val changes = mutableListOf<Boolean>()

    @Test
    fun `the sound switch is reachable and reflects the stored preference`() {
        render(soundEnabled = true)

        scrollToSound()
        compose.onNodeWithText(SOUND).assertIsOn()
    }

    @Test
    fun `turning it off reports the change`() {
        render(soundEnabled = true)

        scrollToSound()
        compose.onNodeWithText(SOUND).performClick()

        assertEquals(listOf(false), changes)
    }

    @Test
    fun `an off preference draws an off switch`() {
        render(soundEnabled = false)

        scrollToSound()
        compose.onNodeWithText(SOUND).assertIsOff()
    }

    /**
     * Sound and vibration are separate switches.
     *
     * The pair matters: wiring the new row to `onHapticsChange`, or reading
     * `hapticsEnabled` for its state, would pass every test above.
     */
    @Test
    fun `sound and vibration are not the same switch`() {
        render(soundEnabled = false, hapticsEnabled = true)

        scrollToSound()
        compose.onNodeWithText(SOUND).assertIsOff()
        compose.onNodeWithText(VIBRATION).assertIsOn()
    }

    private fun scrollToSound() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(SOUND))
    }

    private fun render(soundEnabled: Boolean, hapticsEnabled: Boolean = true) {
        compose.setContent {
            RepForthPreviewHost {
                SettingsScreen(
                    state = SettingsUiState(
                        preferences = UserPreferences.Default.copy(
                            soundEnabled = soundEnabled,
                            hapticsEnabled = hapticsEnabled,
                        ),
                    ),
                    onGoalChange = {},
                    onExperienceChange = {},
                    onScheduleChange = { _, _ -> },
                    onEquipmentChange = {},
                    onThemeChange = {},
                    onLanguageChange = {},
                    onUnitsChange = {},
                    onKeepScreenOnChange = {},
                    onHapticsChange = {},
                    onExcludedMusclesChange = {},
                    onPreferredMusclesChange = {},
                    onMovementExclusionsChange = {},
                    onMovementEditorOpened = {},
                    onExcludedExercisesOpened = {},
                    onExcludedExerciseRemoved = {},
                    onSoundChange = { changes += it },
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
    }

    private companion object {
        const val SOUND = "Sound"
        const val VIBRATION = "Vibration"
    }
}
