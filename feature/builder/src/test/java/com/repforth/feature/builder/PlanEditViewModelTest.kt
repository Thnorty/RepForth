package com.repforth.feature.builder

import com.repforth.core.ai.AiGenerationFailureReason
import com.repforth.core.ai.AiPlanChange
import com.repforth.core.ai.AiPlanEditOutcome
import com.repforth.core.ai.AiWorkoutGenerationOutcome
import com.repforth.core.ai.PlanEditLimits
import com.repforth.core.ai.PlanEditOp
import com.repforth.core.ai.PlanRef
import com.repforth.core.ai.ProviderFailure
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.model.Language
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.testing.FakeProfiles
import com.repforth.core.testing.sampleProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coach's plan edit as the builder holds it: asked, worked on, reviewed, and
 * only then -- if the person says so -- put into the draft. Nothing here saves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var templates: RecordingTemplateRepository
    private lateinit var weeks: RecordingWeekRepository
    private lateinit var catalog: FakeExercises
    private lateinit var editor: FakePlanEditService
    private lateinit var profiles: FakeProfiles
    private lateinit var viewModel: BuilderViewModel

    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        templates = RecordingTemplateRepository()
        weeks = RecordingWeekRepository()
        catalog = FakeExercises()
        editor = FakePlanEditService()
        profiles = FakeProfiles(sampleProfile(id = "p"))
        viewModel = BuilderViewModel(
            templates,
            catalog,
            profiles,
            FakeWorkoutGenerator(),
            editor,
            AlwaysConfigured,
            weeks,
            UserPreferencesDataSource(FakePreferencesStore()),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun openStandalone() {
        templates.save(BuilderFixtures.STANDALONE)
        viewModel.load(BuilderFixtures.STANDALONE.id)
    }

    private suspend fun openWeek() {
        weeks.save(BuilderFixtures.WEEK)
        viewModel.loadWeek(BuilderFixtures.WEEK.id)
    }

    private fun replaceFirst(id: String = "ex-new") =
        AiPlanChange(PlanEditOp.REPLACE, 1, 1, id, 4, 8, null, null, 90)

    @Test
    fun `opening from a card pins that exercise`() = runTest(dispatcher) {
        openWeek()
        advanceUntilIdle()

        viewModel.onPlanEditOpen(PlanRef(2, 1))

        val asking = state.planEdit as PlanEditState.Asking
        assertEquals(PinnedExercise(PlanRef(2, 1), "Exercise 0", 2), asking.pin)
        assertEquals("", asking.text)
    }

    @Test
    fun `a blank instruction is not sent`() = runTest(dispatcher) {
        openStandalone()
        advanceUntilIdle()
        viewModel.onPlanEditOpen(null)

        viewModel.onPlanEditSend("   ", Language.ENGLISH)
        advanceUntilIdle()

        assertTrue(editor.requests.isEmpty())
        assertTrue(state.planEdit is PlanEditState.Asking)
    }

    @Test
    fun `typed text is capped at maxInstructionChars`() = runTest(dispatcher) {
        openStandalone()
        advanceUntilIdle()
        viewModel.onPlanEditOpen(null)

        viewModel.onPlanEditTextChange("x".repeat(2_000))

        assertEquals(PlanEditLimits.maxInstructionChars, (state.planEdit as PlanEditState.Asking).text.length)
    }

    @Test
    fun `send carries the plan, the pin and the locale`() = runTest(dispatcher) {
        openWeek()
        advanceUntilIdle()
        viewModel.onPlanEditOpen(PlanRef(2, 1))

        viewModel.onPlanEditSend("swap it", Language.TURKISH)
        advanceUntilIdle()

        val (request, locale) = editor.requests.single()
        assertEquals(PlanRef(2, 1), request.focus)
        assertEquals("swap it", request.instruction)
        assertEquals(state.toEditablePlan(), request.plan)
        assertEquals(Language.TURKISH, locale)
    }

    @Test
    fun `a proposal is reviewed, not applied`() = runTest(dispatcher) {
        openStandalone()
        advanceUntilIdle()
        val before = state.exercises
        editor.changes = listOf(replaceFirst())
        viewModel.onPlanEditOpen(PlanRef(1, 1))

        viewModel.onPlanEditSend("swap it", Language.ENGLISH)
        advanceUntilIdle()

        val reviewing = state.planEdit as PlanEditState.Reviewing
        assertEquals("Because.", reviewing.rationale)
        assertEquals("Exercise new", (reviewing.rows.single() as PlanEditRow.Replaced).to)
        assertEquals(before, state.exercises)
        assertFalse(state.isDirty)
    }

    @Test
    fun `apply puts the proposal in the draft and makes it dirty without saving`() = runTest(dispatcher) {
        openStandalone()
        advanceUntilIdle()
        editor.changes = listOf(replaceFirst())
        viewModel.onPlanEditOpen(PlanRef(1, 1))
        viewModel.onPlanEditSend("swap it", Language.ENGLISH)
        advanceUntilIdle()
        val saves = templates.saved.size

        viewModel.onPlanEditApply()

        assertEquals("Exercise new", state.exercises.first().name)
        assertTrue(state.isDirty)
        assertEquals(PlanEditState.Closed, state.planEdit)
        assertEquals(saves, templates.saved.size)
    }

    @Test
    fun `a week proposal lands in the right day`() = runTest(dispatcher) {
        openWeek()
        advanceUntilIdle()
        editor.changes = listOf(AiPlanChange(PlanEditOp.ADD, 3, 0, "ex-new", 3, 10, null, null, 60))
        viewModel.onPlanEditOpen(null)
        viewModel.onPlanEditSend("add more legs", Language.ENGLISH)
        advanceUntilIdle()

        viewModel.onPlanEditApply()

        assertEquals("Exercise new", state.weekDays[2].exercises.first().name)
        assertEquals(5, state.weekDays[0].exercises.size)
    }

    @Test
    fun `touched days report their new length`() = runTest(dispatcher) {
        openWeek()
        advanceUntilIdle()
        editor.changes = listOf(AiPlanChange(PlanEditOp.ADD, 3, 0, "ex-new", 3, 10, null, null, 60))
        viewModel.onPlanEditOpen(null)
        viewModel.onPlanEditSend("add more legs", Language.ENGLISH)
        advanceUntilIdle()

        val reviewing = state.planEdit as PlanEditState.Reviewing
        assertEquals(setOf(3), reviewing.dayMinutes.keys)
        assertTrue(reviewing.dayMinutes.getValue(3) > state.weekDays[2].estimatedMinutes)
    }

    @Test
    fun `discard leaves the draft exactly as it was`() = runTest(dispatcher) {
        openStandalone()
        advanceUntilIdle()
        val before = state
        editor.changes = listOf(replaceFirst())
        viewModel.onPlanEditOpen(null)
        viewModel.onPlanEditSend("swap it", Language.ENGLISH)
        advanceUntilIdle()

        viewModel.onPlanEditDismiss()

        assertEquals(before.exercises, state.exercises)
        assertEquals(PlanEditState.Closed, state.planEdit)
        assertFalse(state.isDirty)
    }

    @Test
    fun `a hand edit while reviewing makes the proposal stale and apply does nothing`() = runTest(dispatcher) {
        openStandalone()
        advanceUntilIdle()
        editor.changes = listOf(replaceFirst())
        viewModel.onPlanEditOpen(null)
        viewModel.onPlanEditSend("swap it", Language.ENGLISH)
        advanceUntilIdle()

        viewModel.onSetsChange(1, 9)
        assertTrue(state.planEditIsStale)
        val handEdited = state.exercises
        viewModel.onPlanEditApply()

        assertEquals(handEdited, state.exercises)
        assertTrue(state.planEdit is PlanEditState.Reviewing)
    }

    @Test
    fun `a rename while reviewing is not stale`() = runTest(dispatcher) {
        openStandalone()
        advanceUntilIdle()
        editor.changes = listOf(replaceFirst())
        viewModel.onPlanEditOpen(null)
        viewModel.onPlanEditSend("swap it", Language.ENGLISH)
        advanceUntilIdle()

        viewModel.onNameChange("Renamed")

        assertFalse(state.planEditIsStale)
        viewModel.onPlanEditApply()
        assertEquals("Renamed", state.name)
        assertEquals("Exercise new", state.exercises.first().name)
    }

    @Test
    fun `a failure keeps the instruction and pin, and retry resends them`() = runTest(dispatcher) {
        openStandalone()
        advanceUntilIdle()
        editor.outcome = AiPlanEditOutcome.Failure(
            AiWorkoutGenerationOutcome.Failure(
                AiGenerationFailureReason.PROVIDER_FAILURE,
                attempts = 1,
                providerFailure = ProviderFailure.NETWORK,
            ),
        )
        viewModel.onPlanEditOpen(PlanRef(1, 2))
        viewModel.onPlanEditSend("swap it", Language.ENGLISH)
        advanceUntilIdle()

        val failed = state.planEdit as PlanEditState.Failed
        assertEquals("swap it", failed.instruction)
        assertEquals(PlanRef(1, 2), failed.pin?.ref)
        assertTrue(failed.error.canRetry)

        editor.outcome = null
        viewModel.onPlanEditRetry(Language.ENGLISH)
        advanceUntilIdle()

        assertEquals(2, editor.requests.size)
        assertEquals(editor.requests[0].first.instruction, editor.requests[1].first.instruction)
        assertEquals(PlanRef(1, 2), editor.requests[1].first.focus)
        assertTrue(state.planEdit is PlanEditState.Reviewing)
    }

    @Test
    fun `cancel while working stops the job and returns to asking with the text kept`() = runTest(dispatcher) {
        openStandalone()
        advanceUntilIdle()
        editor.gate = CompletableDeferred()
        editor.changes = listOf(replaceFirst())
        viewModel.onPlanEditOpen(null)
        viewModel.onPlanEditSend("swap it", Language.ENGLISH)
        advanceUntilIdle()
        assertTrue(state.planEdit is PlanEditState.Working)

        viewModel.onPlanEditCancel()
        editor.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(PlanEditState.Asking(pin = null, text = "swap it"), state.planEdit)
    }

    @Test
    fun `zero changes reviews with no rows`() = runTest(dispatcher) {
        openStandalone()
        advanceUntilIdle()
        editor.changes = emptyList()
        editor.rationale = "Already balanced."
        viewModel.onPlanEditOpen(null)
        viewModel.onPlanEditSend("anything?", Language.ENGLISH)
        advanceUntilIdle()

        val reviewing = state.planEdit as PlanEditState.Reviewing
        assertTrue(reviewing.rows.isEmpty())
        assertEquals("Already balanced.", reviewing.rationale)
    }

    @Test
    fun `no profile fails with the onboarding message`() = runTest(dispatcher) {
        profiles = FakeProfiles(null)
        viewModel = BuilderViewModel(
            templates, catalog, profiles, FakeWorkoutGenerator(), editor, AlwaysConfigured, weeks,
            UserPreferencesDataSource(FakePreferencesStore()),
        )
        openStandalone()
        advanceUntilIdle()
        viewModel.onPlanEditOpen(null)

        viewModel.onPlanEditSend("swap it", Language.ENGLISH)
        advanceUntilIdle()

        val failed = state.planEdit as PlanEditState.Failed
        assertEquals(R.string.coach_failed_no_profile, failed.error.messageRes)
        assertFalse(failed.error.canRetry)
        assertTrue(editor.requests.isEmpty())
    }
}
