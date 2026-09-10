package com.repforth.wear

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.WATCH_SCREENSHOT_DEVICE
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The page that shows the exercise moving.
 *
 * §3 shipped a "compact static thumbnail" and said an animation would be a later
 * opt-in. The owner asked for it on 2026-09-10, and the phone now sends the GIF
 * where it used to send the JPEG — about 94KB against 6.6KB at the catalog's
 * median, and 233KB at its worst.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = WATCH_SCREENSHOT_DEVICE)
class MediaPageComposeTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * **The fixture has to be genuinely animated, or the rest of this proves
     * nothing.**
     *
     * Every other test here would pass just as well against a still image, so
     * this is the one that keeps them honest: if `TestMedia` ever degrades to a
     * PNG, the page could stop animating entirely and nothing else would notice.
     */
    @Test
    fun `the fixture is an animated image, not a still`() {
        val decoded = ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(ByteBuffer.wrap(animatedGif())),
        )

        assertTrue(
            "TestMedia must stay an animated GIF: it is what makes this page's " +
                "reason for existing testable",
            decoded is AnimatedImageDrawable,
        )
    }

    /**
     * §6's notice travels with the picture and has to be legible over it.
     *
     * Gated on a decoded image rather than on the phone having sent bytes: a
     * copyright line under a picture the user cannot see is a claim about
     * nothing.
     */
    @Test
    fun `the attribution is drawn with the picture`() {
        render(media = animatedGif(), attributed = true)

        compose.onNodeWithText(NOTICE).assertIsDisplayed()
    }

    /**
     * Bytes that are not an image draw nothing, rather than taking the app down.
     *
     * The phone sends whatever the media cache holds, and a truncated or
     * half-written file is a real thing to receive. §15 keeps the workout
     * working whatever the media is doing.
     */
    @Test
    fun `bytes that are not an image draw nothing and do not crash`() {
        render(media = byteArrayOf(1, 2, 3, 4), attributed = true)

        assertEquals(
            "There is no picture, so there is nothing to attribute",
            0,
            compose.onAllNodesWithText(NOTICE).fetchSemanticsNodes().size,
        )
    }

    private fun render(media: ByteArray, attributed: Boolean) {
        compose.setContent {
            RepForthWearTheme {
                MediaPage(
                    state = state(attributed),
                    media = media,
                    // Never true here. A playing AnimatedImageDrawable keeps
                    // invalidating, so the composition never idles and the test
                    // hangs -- the same trap AGENTS.md records for a frame-driven
                    // rest ring. The pager is what turns playback on, and only
                    // for the page being looked at.
                    playing = false,
                )
            }
        }
    }

    private fun state(attributed: Boolean) = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = WearPhase.Exercise,
        exerciseId = "0025",
        exerciseName = "Barbell Decline Wide-Grip Press",
        setNumber = 1,
        totalSets = 3,
        targetReps = 12,
        targetDurationMs = null,
        restDeadlineElapsedRealtimeMs = null,
        setDeadlineElapsedRealtimeMs = null,
        nextExerciseName = null,
        mediaAttribution = NOTICE.takeIf { attributed },
    )

    private companion object {
        const val NOTICE = "© Gym visual — https://gymvisual.com/"
    }
}
