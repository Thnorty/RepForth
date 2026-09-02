package com.repforth.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.testing.ENGLISH
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
 * What Settings actually looks like, in both languages and at both font scales.
 *
 * Three of this project's recent defects lived on this screen and every one of
 * them was invisible to the tests that already existed: choice pills that broke
 * onto a second line inside themselves, a row whose two halves ran past each
 * other, and a list that opened already scrolled. Unit tests asserted the state
 * was right — it was — and passed the whole time.
 *
 * The matrix is the point. `AGENTS.md` requires layouts to survive Turkish,
 * which runs 15-30% longer than English, and text to survive 200% font scale;
 * neither had ever been checked by anything but a person holding a phone. Four
 * renders per screen costs seconds on the JVM.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class SettingsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settings_english() = captureSettings("settings-en")

    @Test
    fun settings_turkish() = captureSettings("settings-tr", locale = TURKISH)

    /**
     * The case that has broken twice.
     *
     * Doubling the font scale is what turns a row that merely fits into one that
     * does not, and it is the configuration nobody has on their own phone.
     */
    @Test
    fun settings_english_large_text() = captureSettings("settings-en-2x", fontScale = 2f)

    @Test
    fun settings_turkish_large_text() =
        captureSettings("settings-tr-2x", locale = TURKISH, fontScale = 2f)

    private fun captureSettings(
        name: String,
        locale: String? = null,
        fontScale: Float = 1f,
    ) {
        // Both, always, even at their defaults. Robolectric carries
        // qualifiers across test methods in the same JVM, so a test that set
        // only what it changed rendered in whatever the previous one left
        // behind -- English goldens came out Turkish when the suite ran in a
        // different order, and passed when the class ran alone.
        RuntimeEnvironment.setQualifiers("+${locale ?: ENGLISH}")
        RuntimeEnvironment.setFontScale(fontScale)

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

        compose.onRoot().captureRoboImage(screenshotPath(name))
    }

    private companion object {
        /**
         * The longest options in both languages.
         *
         * A screenshot of the shortest label proves nothing: "Strength" fits
         * anywhere, and it was "General fitness" and "More than 3 years" that
         * broke. Hypertrophy and Advanced are picked so the selected chip is one
         * of the long ones.
         */
        val SAMPLE_PROFILE = UserProfile(
            id = "screenshot",
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
