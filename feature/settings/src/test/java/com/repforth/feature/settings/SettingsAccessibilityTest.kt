package com.repforth.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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

    /**
     * The two dialogs nothing could reach.
     *
     * Both are opened by state held inside `SettingsScreen`, and the plan
     * recorded that as needing the state hoisted out for a test to get at them.
     * It does not: a test can tap the row the same way a user does, which
     * covers the dialog *and* the row that opens it. Hoisting would have
     * changed the screen for the test's benefit and tested less.
     *
     * The equipment dialog is the one with something to catch. Its rows carry
     * `Role.Checkbox` on a `toggleable`, and a deliberate break of that role
     * was made once and caught by nothing, because no test could open the
     * dialog to look.
     */
    @Test
    fun equipment_dialog_english() = checkDialog(ENGLISH, R.string.settings_profile_equipment)

    @Test
    fun equipment_dialog_turkish() = checkDialog(TURKISH, R.string.settings_profile_equipment)

    /**
     * The half of the equipment dialog that is behind a button.
     *
     * `Equipment.UNCOMMON` renders only after "More equipment" is tapped, so
     * the tests above reach it no more than they reached the dialog itself.
     * Found by breaking one role at a time: removing it from the uncommon rows
     * failed nothing, because nothing had ever drawn them.
     */
    @Test
    fun equipment_dialog_expanded_english() = checkDialog(
        locale = ENGLISH,
        rowLabel = R.string.settings_profile_equipment,
        expand = true,
    )

    @Test
    fun equipment_dialog_expanded_turkish() = checkDialog(
        locale = TURKISH,
        rowLabel = R.string.settings_profile_equipment,
        expand = true,
    )

    @Test
    fun schedule_dialog_english() = checkDialog(ENGLISH, R.string.settings_profile_schedule)

    @Test
    fun schedule_dialog_turkish() = checkDialog(TURKISH, R.string.settings_profile_schedule)

    /** Opens a dialog by tapping its row, then checks everything on screen. */
    private fun checkDialog(locale: String, rowLabel: Int, expand: Boolean = false) {
        render(locale)

        // Resolved from resources rather than written out, so the Turkish run
        // taps the Turkish row instead of silently finding nothing.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val label = context.getString(rowLabel)
        compose.onNodeWithText(label).performClick()

        if (expand) {
            compose.onNodeWithText(
                context.getString(
                    R.string.settings_profile_equipment_more,
                    Equipment.UNCOMMON.size,
                ),
            ).performClick()
        }

        // Stated, not assumed. `assertScreenIsAccessible` passes on an empty
        // tree by design, so a tap that silently did nothing would leave four
        // green tests that inspect the screen behind the dialog and nothing
        // else -- which is the shape of every green-but-reaching-nothing bug
        // this repo has already paid for.
        compose.onNode(isDialog()).assertExists()

        compose.assertScreenIsAccessible("Settings dialog $label ($locale)")
    }

    private fun check(locale: String) {
        render(locale)
        compose.assertScreenIsAccessible("Settings ($locale)")
    }

    private fun render(locale: String) {
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
