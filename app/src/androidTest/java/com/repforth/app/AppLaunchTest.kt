package com.repforth.app

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app starts, and every screen it can reach can be reached.
 *
 * These exist because of what the JVM tests could not see. Two hundred and
 * sixty-five of them were green while the app crashed on opening Settings, drew
 * onboarding under the camera cutout, and could not select day six of seven —
 * every one of those a defect in Android's own plumbing rather than in this
 * project's logic, and every one found by a person tapping the screen.
 *
 * So the bar here is deliberately low and broad: open things and assert they
 * opened. A test that merely proves a screen composes without throwing would
 * have caught two of the five.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hilt.inject()
    }

    /**
     * A fresh install shows the questionnaire.
     *
     * Also the smoke test for the whole graph: reaching this assertion means
     * Room opened the packaged catalog, DataStore read, Hilt built every
     * dependency, and Compose drew a frame.
     */
    @Test
    fun onboardingIsShownBeforeAProfileExists() {
        // Exactly what `awaitFirstScreen` waits for, which is the point: this
        // test asserts that the wait terminates on a real first frame, and a
        // second copy of the condition could drift away from the one every
        // other test depends on.
        compose.awaitFirstScreen()
    }

    /**
     * Every tab opens.
     *
     * Each one builds its own Hilt ViewModel, which is exactly what the
     * `LocalContext` change broke: the failure was not in any screen's code but
     * in the activity being unreachable from the context they were given.
     */
    @Test
    fun everyTabOpens() {
        compose.completeOnboardingIfShown()

        listOf(AppText.plans, AppText.exercises, AppText.progress, AppText.today).forEach { tab ->
            compose.onNodeWithText(tab).performClick()
            compose.waitForIdle()
        }
    }

    /**
     * Settings opens.
     *
     * Its own test because it is the screen that crashed: it is reached from
     * the top bar rather than a tab, and it builds a ViewModel that reaches
     * three other modules.
     */
    @Test
    fun settingsOpens() {
        compose.completeOnboardingIfShown()

        compose.onNodeWithContentDescription(AppText.settings).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(AppText.appearance).assertExists()
    }
}
