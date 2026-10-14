package com.repforth.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.body.BodyRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Body tab's two writes, walked through the real shell.
 *
 * Each screen has its own Robolectric tests; what those cannot show is the
 * glue -- that the Progress tab reaches Body, that "Check in" and "Choose what
 * to track" navigate where they say, and that what is saved comes back on the
 * tab. The add dialog in particular cannot be driven under Robolectric at all
 * (see `TrackingComposeTest`), so this is the only test of it.
 *
 * **State is not reset between methods**, as in `StartConflictTest`: the check-in
 * edits today's if one exists, and the custom type is deleted before it is added.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BodyFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var body: BodyRepository

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking {
            body.observeTypes().first()
                .filter { it.customName == CUSTOM }
                .forEach { body.deleteCustom(it.id) }
        }
        compose.completeOnboardingIfShown()
    }

    @Test
    fun checkInShowsOnTheBodyTab() {
        openBody()

        compose.onNodeWithText(AppText.checkIn).performClick()
        compose.waitUntil(TIMEOUT) { fieldNamed(AppText.waist).isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText(AppText.waist)).performTextReplacement("86")
        compose.onNodeWithText(AppText.checkInSave).performClick()

        // The row merges its name and figure, so both are on one node.
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText(AppText.waist) and hasText("86")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aCustomMeasurementIsAddedThroughTheDialog() {
        openBody()

        compose.onNodeWithText(AppText.chooseTracked).performClick()
        compose.onNodeWithText(AppText.addCustom).performClick()
        compose.waitUntil(TIMEOUT) { fieldNamed(AppText.customName).isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText(AppText.customName)).performTextReplacement(CUSTOM)
        // The kind option, not the Weight switch row behind the dialog.
        compose.onNode(hasText(AppText.kindWeight) and isSelectable()).performClick()
        compose.onNodeWithText(AppText.addCustomConfirm).performClick()

        // Stored first, then shown: the new type joins the end of the list,
        // below fifteen built-ins, and a lazy list composes nothing off screen.
        compose.waitUntil(TIMEOUT) {
            runBlocking { body.observeTypes().first().any { it.customName == CUSTOM } }
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(CUSTOM))
        compose.onNodeWithText(CUSTOM).assertIsDisplayed()
    }

    private fun openBody() {
        compose.openTab(AppText.progress)
        compose.onNodeWithText(AppText.bodyTab).performClick()
        compose.waitForIdle()
    }

    private fun fieldNamed(label: String) =
        compose.onAllNodes(hasSetTextAction() and hasText(label)).fetchSemanticsNodes()

    private companion object {
        const val TIMEOUT = 10_000L

        /** Distinct, so nothing another test saved can be mistaken for it. */
        const val CUSTOM = "Body flow test grip"
    }
}
