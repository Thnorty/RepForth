package com.repforth.feature.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dialog's three ways out.
 *
 * The two buttons were always there. Dismissing was blocked — `onDismissRequest`
 * was an empty lambda — which silently disabled *both* the back gesture and a
 * tap outside, since `AlertDialog` routes them to the same place. That is worth
 * a test rather than a reading of the source: an empty lambda there compiles,
 * looks deliberate, and produces a dialog that traps the user with no error
 * anywhere.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class WorkoutConflictDialogComposeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var kept = 0
    private var discarded = 0
    private var cancelled = 0

    /** The one that regressed: back used to do nothing at all. */
    @Test
    fun `back dismisses without answering`() {
        render()

        Espresso.pressBack()
        compose.waitForIdle()

        assertEquals(1, cancelled)
        assertEquals("Back must not answer the question", 0, kept + discarded)
    }

    @Test
    fun `going back to the running workout is its own answer`() {
        render()

        compose.onNodeWithText("Go back to it").performClick()

        assertEquals(1, kept)
        assertEquals(0, cancelled + discarded)
    }

    @Test
    fun `discarding is its own answer`() {
        render()

        compose.onNodeWithText("Discard and start").performClick()

        assertEquals(1, discarded)
        assertEquals(0, cancelled + kept)
    }

    /** The name is the content: "a workout" is useless if you forgot which. */
    @Test
    fun `the running workout is named`() {
        render()

        compose.onNodeWithText("Push day", substring = true).assertExists()
    }

    /** A workout begun from no plan still gets a readable sentence. */
    @Test
    fun `an unnamed workout does not leave a hole in the sentence`() {
        render(runningName = null)

        compose.onNodeWithText("Your workout", substring = true).assertExists()
    }

    private fun render(runningName: String? = "Push day") {
        compose.setContent {
            RepForthPreviewHost {
                WorkoutConflictDialog(
                    runningName = runningName,
                    onKeep = { kept++ },
                    onDiscard = { discarded++ },
                    onCancel = { cancelled++ },
                )
            }
        }
    }
}
