package com.repforth.feature.builder

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.ai.AiPlanChange
import com.repforth.core.ai.PlanEditOp
import com.repforth.core.ai.PlanRef
import com.repforth.core.ai.ProviderAvailability
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.testing.FakeProfiles
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.sampleProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Coach's plan edit, driven through the real builder screen and view model.
 *
 * This is the test that proves the screen reaches the call. A view-model test
 * can show that `onPlanEditSend` builds the right request; it cannot show that
 * tapping a card's Coach button gets there -- the gap that let two green fixes
 * change nothing on a device (AGENTS.md, "Prove the fix is reached").
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class PlanEditComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val templates = RecordingTemplateRepository()
    private val weeks = RecordingWeekRepository()
    private val editor = FakePlanEditService()

    @Before
    fun locale() {
        RuntimeEnvironment.setQualifiers("+en")
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun render(
        planId: String? = null,
        weekId: String? = null,
        configured: Boolean = true,
    ) {
        runBlocking {
            templates.save(BuilderFixtures.STANDALONE)
            weeks.save(BuilderFixtures.WEEK)
        }
        val viewModel = BuilderViewModel(
            templates,
            FakeExercises(),
            FakeProfiles(sampleProfile(id = "p")),
            FakeWorkoutGenerator(),
            editor,
            if (configured) AlwaysConfigured else NotConfigured,
            weeks,
            UserPreferencesDataSource(FakePreferencesStore()),
        )
        compose.setContent {
            RepForthPreviewHost {
                BuilderRoute(onSaved = {}, planId = planId, weekId = weekId, viewModel = viewModel)
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Exercise 0").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun swapFirstFor(id: String) {
        editor.changes = listOf(AiPlanChange(PlanEditOp.REPLACE, 1, 1, id, 4, 8, null, null, 90))
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun absent(text: String) =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()

    @Test
    fun `a card's Coach button, the swap chip and Apply change that card`() {
        swapFirstFor("ex-new")
        render(planId = BuilderFixtures.STANDALONE.id)

        compose.onNodeWithContentDescription("Ask Coach about Exercise 0").performClick()
        compose.onNodeWithText(SWAP_CHIP).performClick()
        waitFor(APPLY)
        compose.onNodeWithText(APPLY).performClick()
        waitFor("Exercise new")

        assertTrue("The replaced exercise must be gone", absent("Exercise 0"))
        val (request, _) = editor.requests.single()
        assertEquals(PlanRef(1, 1), request.focus)
        assertEquals(SWAP_INSTRUCTION, request.instruction)
    }

    @Test
    fun `the plan-wide action sends without a pin`() {
        render(planId = BuilderFixtures.STANDALONE.id)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText(OPEN_PLAN_WIDE))
        compose.onNodeWithText(OPEN_PLAN_WIDE).performClick()
        compose.onNodeWithText(HINT).performTextInput("add more chest")
        compose.onNodeWithText(SEND).performClick()
        compose.waitUntil(5_000) { editor.requests.isNotEmpty() }

        val (request, _) = editor.requests.single()
        assertNull(request.focus)
        assertEquals("add more chest", request.instruction)
    }

    @Test
    fun `discard leaves the cards as they were`() {
        swapFirstFor("ex-new")
        render(planId = BuilderFixtures.STANDALONE.id)

        compose.onNodeWithContentDescription("Ask Coach about Exercise 0").performClick()
        compose.onNodeWithText(SWAP_CHIP).performClick()
        waitFor(DISCARD)
        compose.onNodeWithText(DISCARD).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Exercise 0").assertIsDisplayed()
        assertTrue(absent("Exercise new"))
    }

    @Test
    fun `without a provider the sheet offers settings and no send`() {
        render(planId = BuilderFixtures.STANDALONE.id, configured = false)

        compose.onNodeWithContentDescription("Ask Coach about Exercise 0").performClick()

        compose.onNodeWithText(SETUP_NOTICE, substring = true).assertIsDisplayed()
        compose.onNodeWithText(SEND).assertIsNotEnabled()
    }

    @Test
    fun `a week card pins its own day`() {
        render(weekId = BuilderFixtures.WEEK.id)

        // Only the first day starts open; close it and open the second, so the
        // card tapped is unambiguously day 2's.
        compose.onNodeWithContentDescription("Collapse").performClick()
        compose.onNodeWithText("Back and biceps", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Ask Coach about Exercise 0").performClick()
        compose.onNodeWithText(SWAP_CHIP).performClick()
        compose.waitUntil(5_000) { editor.requests.isNotEmpty() }

        assertEquals(PlanRef(2, 1), editor.requests.single().first.focus)
    }

    /**
     * Back while Coach works asks first, and "Keep waiting" keeps the sheet.
     *
     * The sheet refused to become Hidden while working, but material3's back
     * handler animates it off-screen before asking whether it may: the sheet
     * slid away, the confirm dialog came up, and keeping the request left a
     * scrim over the builder and the answer arriving somewhere nobody could see
     * it. So this asserts the answer is *displayed*, not merely composed.
     */
    @Test
    fun `back while Coach works, then keep waiting, still shows the answer`() {
        swapFirstFor("ex-new")
        val gate = CompletableDeferred<Unit>()
        editor.gate = gate
        render(planId = BuilderFixtures.STANDALONE.id)

        compose.onNodeWithContentDescription("Ask Coach about Exercise 0").performClick()
        compose.onNodeWithText(SWAP_CHIP).performClick()
        waitFor(WORKING)

        Espresso.pressBack()
        waitFor(KEEP_WAITING)
        compose.onNodeWithText(KEEP_WAITING).performClick()
        compose.waitForIdle()

        gate.complete(Unit)
        waitFor(APPLY)
        compose.onNodeWithText(APPLY).assertIsDisplayed()
    }

    private object NotConfigured : ProviderAvailability {
        override val configured = flowOf(false)
    }

    private companion object {
        const val OPEN_PLAN_WIDE = "Ask Coach to change this"
        const val SWAP_CHIP = "Swap for something similar"
        const val SWAP_INSTRUCTION = "Replace this exercise with a different one that works the same muscles."
        const val HINT = "What should change?"
        const val SEND = "Ask Coach"
        const val APPLY = "Apply"
        const val DISCARD = "Discard"
        const val SETUP_NOTICE = "AI provider you choose"
        const val WORKING = "Coach is reworking your plan"
        const val KEEP_WAITING = "Keep waiting"
    }
}
