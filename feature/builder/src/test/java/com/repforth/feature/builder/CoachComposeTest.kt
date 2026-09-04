package com.repforth.feature.builder

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Coach saying what it needs, before it needs it.
 *
 * "Build one for me" was offered unconditionally. There is no built-in planner
 * to fall back to — `RulesEngine` filters and validates candidates and cannot
 * build a plan — so on an install with no provider the screen could only fail,
 * and it failed on the last tap: after the muscles, the days and the session
 * length had all been chosen, with a dialog that named Settings and had no way
 * to open it.
 *
 * Two things are asserted because either alone is a half-fix. Saying so is no
 * use if the button still invites the tap, and disabling the button is no use
 * if nothing says why.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class CoachComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an unconfigured provider is said before the form, not after it`() {
        render(configured = false)

        compose.onNodeWithText(SETUP_NOTICE, substring = true).assertIsDisplayed()
        compose.onNodeWithText(GENERATE).assertIsNotEnabled()
    }

    @Test
    fun `the notice offers the way to fix it`() {
        var opened = 0
        render(configured = false, onOpenProviderSettings = { opened++ })

        compose.onNodeWithText(SETUP_OPEN).performClick()

        assertEquals("The notice must open provider settings", 1, opened)
    }

    @Test
    fun `a configured provider says nothing and lets it run`() {
        render(configured = true)

        assertTrue(
            "A set-up install must not be told to set up",
            compose.onAllNodesWithText(SETUP_OPEN).fetchSemanticsNodes().isEmpty(),
        )
        compose.onNodeWithText(GENERATE).assertIsEnabled()
    }

    private fun render(
        configured: Boolean,
        onOpenProviderSettings: () -> Unit = {},
    ) {
        compose.setContent {
            RepForthPreviewHost {
                CoachScreen(
                    // Reduced motion, so the button's glow does not start an
                    // infinite transition. A composition that never goes idle
                    // hangs every Robolectric test that renders this screen.
                    state = BuilderUiState(
                        coaching = true,
                        reducedMotion = true,
                        providerConfigured = configured,
                    ),
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
                    onOpenProviderSettings = onOpenProviderSettings,
                )
            }
        }
    }

    private companion object {
        /** A fragment, so the assertion does not restate the whole sentence. */
        const val SETUP_NOTICE = "AI provider you choose"
        const val SETUP_OPEN = "Set up a provider"
        const val GENERATE = "Build it"
    }
}
