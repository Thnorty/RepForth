package com.repforth.feature.body

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.Pose
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.FakeBodyRepository
import com.repforth.core.testing.FakePhotoStore
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The check-in screen driven through its real route and view model.
 *
 * The camera and the photo picker are other apps, so the screen's own
 * launchers are answered by a test registry — which still proves the tap
 * reaches the launcher and the answer reaches the photo store (AGENTS.md,
 * "Prove the fix is reached").
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class CheckInComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val body = FakeBodyRepository(today = BodyFixtures.TODAY)
    private val photos = FakePhotoStore()
    private var done = false
    private val launched = mutableListOf<ActivityResultContract<*, *>>()

    /** Answers every launch at once: a picked image, or a camera that wrote its file. */
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launched += contract
            val answer: Any = if (input is Uri) true else Uri.parse("content://picked/1")
            dispatchResult(requestCode, answer)
        }
    }

    @Before
    fun locale() {
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun render(cameraAvailable: Boolean = true) {
        val viewModel = CheckInViewModel(
            SavedStateHandle(), body, photos, FakeTimeSource(wallClock = 1_791_892_800_000L), ZoneOffset.UTC,
        )
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                RepForthPreviewHost {
                    CheckInRoute(onDone = { done = true }, viewModel = viewModel, cameraAvailable = cameraAvailable)
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Waist").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun typeInto(label: String, text: String) =
        compose.onNode(hasSetTextAction() and hasText(label, substring = true)).performTextInput(text)

    @Test
    fun `typing a waist and saving reaches the repository`() {
        render()

        typeInto("Waist", "88")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { done }

        val saved = runBlocking { body.findOn(BodyFixtures.TODAY) }!!
        assertEquals(88.0, saved.measurements.getValue("waist"), 0.0)
    }

    @Test
    fun `choosing a photo fills the slot`() {
        render()

        compose.onAllNodesWithText("Choose photo")[0].performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Remove").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(listOf("photo-0.jpg"), photos.imported)
    }

    @Test
    fun `taking a photo hands the camera a capture file and fills the slot`() {
        render()

        compose.onAllNodesWithText("Take photo")[1].performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Remove").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { done }

        assertEquals(1, photos.captures)
        assertEquals(mapOf(Pose.SIDE to "photo-0.jpg"), runBlocking { body.findOn(BodyFixtures.TODAY) }!!.photos)
    }

    @Test
    fun `without a camera app taking a photo is off and says why`() {
        render(cameraAvailable = false)

        compose.onAllNodesWithText("Take photo")[0].performScrollTo().assertIsNotEnabled()
        assertTrue(compose.onAllNodesWithText("No camera app found").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `back with changes asks first`() {
        render()

        typeInto("Waist", "88")
        compose.waitForIdle()
        // The first back on a phone with the keyboard up closes the keyboard.
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(5_000) { done }

        assertEquals(null, runBlocking { body.findOn(BodyFixtures.TODAY) })
    }
}
