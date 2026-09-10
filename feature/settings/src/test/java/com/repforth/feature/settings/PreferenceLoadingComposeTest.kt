package com.repforth.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.UserPreferences
import com.repforth.core.testing.SCREENSHOT_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings never draws a preference it has not read yet.
 *
 * **Reported from a phone.** With vibration and sound turned off, reopening
 * Settings drew both switches with the thumb over at the "on" side and the
 * "off" colours — a control saying two things at once. The state's
 * `preferences` defaulted to `UserPreferences.Default`, whose vibration and
 * sound are `true`, so the screen drew a guess and corrected it a frame later.
 * A Material switch takes its colours from the new value immediately and
 * *animates* its thumb and its size to match; the colours arrived and the
 * movement did not.
 *
 * The three preferences whose default happened to equal what was stored looked
 * right throughout, which is what made it read as a bug about vibration and
 * sound rather than about guessing.
 *
 * **The goldens could not have caught this and still cannot.** The settings
 * screenshot stops at "Appearance" — every switch on this screen is below the
 * fold of a `LazyColumn`, so it is not composed, not photographed, and not in
 * the semantics tree either. Hence a tall qualifier here rather than a picture:
 * the viewport is 3000dp so the whole list composes and can be asserted on.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TALL_ENOUGH_FOR_THE_WHOLE_LIST)
class PreferenceLoadingComposeTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * **The assertion that actually holds the fix.**
     *
     * The three below render the screen and are worth having, and none of them
     * could have caught this: they pass a value in, so they never exercise what
     * the state does when it has none. The defect was one word in a default.
     * Watched failing by putting `UserPreferences.Default` back.
     */
    @Test
    fun `the initial state carries no preferences at all`() {
        assertNull(
            "Settings must start with nothing rather than a guess: every switch " +
                "whose stored value differs from the default would otherwise be " +
                "drawn wrong and then corrected in front of the user",
            SettingsUiState().preferences,
        )
    }

    @Test
    fun `no switch is drawn before the stored preferences arrive`() {
        render(preferences = null)

        assertEquals(
            "A switch drawn from a guess is a switch that has to correct itself, " +
                "and correcting itself is what left the thumb stranded",
            0,
            compose.onAllNodesWithText(VIBRATION).fetchSemanticsNodes().size,
        )
    }

    /** The pair: hiding them forever would pass the assertion above. */
    @Test
    fun `the switches appear once the preferences have arrived`() {
        render(preferences = UserPreferences.Default)

        compose.onNodeWithText(VIBRATION).assertIsDisplayed()
    }

    /**
     * And a stored `false` is drawn as off, with nothing to animate away from.
     *
     * This is the state the phone got wrong. It cannot reproduce the stranded
     * thumb — Robolectric completes the animation that a device left hanging —
     * so what it holds is the fix rather than the symptom: the screen is handed
     * one value and never a different one first.
     */
    @Test
    fun `a stored off is drawn off`() {
        render(preferences = UserPreferences.Default.copy(hapticsEnabled = false))

        compose.onNodeWithText(VIBRATION).assertIsOff()
    }

    private fun render(preferences: UserPreferences?) {
        compose.setContent {
            RepForthPreviewHost {
                SettingsScreen(
                    state = SettingsUiState(preferences = preferences),
                    onGoalChange = {},
                    onExperienceChange = {},
                    onScheduleChange = { _, _ -> },
                    onEquipmentChange = {},
                    onThemeChange = {},
                    onLanguageChange = {},
                    onUnitsChange = {},
                    onKeepScreenOnChange = {},
                    onHapticsChange = {},
                    onSoundChange = {},
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
        const val VIBRATION = "Vibration"
    }
}

/**
 * A viewport tall enough that the `LazyColumn` composes its whole list.
 *
 * Not a real device, and deliberately so: the switches this asserts on sit
 * below the fold on every phone, and an uncomposed item is absent from the
 * semantics tree — so a test on a realistic screen would pass whether the fix
 * were there or not.
 */
private const val TALL_ENOUGH_FOR_THE_WHOLE_LIST =
    "w411dp-h3000dp-normal-long-notround-any-420dpi-keyshidden-nonav"
