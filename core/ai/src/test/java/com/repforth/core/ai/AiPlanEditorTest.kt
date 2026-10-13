package com.repforth.core.ai

import com.repforth.core.datastore.ProviderSettingsDataSource
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.Language
import com.repforth.core.model.Muscle
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.testing.InMemorySecretStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One plan edit from request to trusted proposal: configuration resolved for the
 * call, server errors retried without spending the repair, one repair for an
 * answer that is malformed or does not apply, and a typed failure otherwise.
 */
class AiPlanEditorTest {

    private val plan = EditablePlan(
        listOf(
            EditableDay(
                "Push",
                listOf(EditableExercise("draft-1", "press", 3, 10, null, null, 60)),
            ),
        ),
    )

    private val catalog = listOf(
        candidate("press"),
        candidate("fly"),
        candidate("barbell-row", equipment = Equipment.BARBELL),
    )

    @Test
    fun `no configuration fails before any call`() = runTest {
        val provider = SequencedEditProvider()
        val harness = harness(provider, configured = false)

        val outcome = harness.editor.edit(request(), Language.ENGLISH, catalog) as AiPlanEditOutcome.Failure

        assertEquals(AiGenerationFailureReason.NO_PROVIDER_CONFIGURATION, outcome.failure.reason)
        assertEquals(0, outcome.failure.attempts)
        assertTrue(provider.calls.isEmpty())
    }

    @Test
    fun `no eligible candidates fails before any call`() = runTest {
        val provider = SequencedEditProvider()
        val harness = harness(provider)

        val outcome = harness.editor.edit(
            request(),
            Language.ENGLISH,
            listOf(candidate("barbell-row", equipment = Equipment.BARBELL)),
        ) as AiPlanEditOutcome.Failure

        assertEquals(AiGenerationFailureReason.NO_ELIGIBLE_CANDIDATES, outcome.failure.reason)
        assertTrue(provider.calls.isEmpty())
    }

    @Test
    fun `a valid proposal is returned with the applied plan`() = runTest {
        val changes = listOf(replace(1, 1, "fly"))
        val harness = harness(SequencedEditProvider(answer(changes)))

        val outcome = harness.editor.edit(request(), Language.ENGLISH, catalog) as AiPlanEditOutcome.Proposal

        val expected = (PlanEditApplier.apply(plan, changes) as PlanEditApplication.Applied).edit.plan
        assertEquals(expected, outcome.edited)
        assertEquals(changes, outcome.changes)
        assertEquals("Because.", outcome.rationale)
        assertEquals(1, outcome.attempts)
    }

    @Test
    fun `zero changes is a proposal, not a failure`() = runTest {
        val harness = harness(SequencedEditProvider(answer(emptyList(), "Already balanced.")))

        val outcome = harness.editor.edit(request(), Language.ENGLISH, catalog) as AiPlanEditOutcome.Proposal

        assertEquals(plan, outcome.edited)
        assertTrue(outcome.changes.isEmpty())
        assertEquals("Already balanced.", outcome.rationale)
    }

    @Test
    fun `a server error is retried without spending the repair attempt`() = runTest {
        val provider = SequencedEditProvider(
            ProviderPlanEditResult.Failed(ProviderFailure.SERVER, "busy"),
            answer(listOf(replace(1, 1, "fly"))),
        )
        val harness = harness(provider)

        val outcome = harness.editor.edit(request(), Language.ENGLISH, catalog)

        assertTrue(outcome is AiPlanEditOutcome.Proposal)
        assertEquals(1, (outcome as AiPlanEditOutcome.Proposal).attempts)
        assertEquals(listOf<AiWorkoutRetryFeedback?>(null, null), provider.calls.map { it.second })
    }

    @Test
    fun `an invalid answer gets one repair attempt with typed feedback`() = runTest {
        val provider = SequencedEditProvider(
            answer(listOf(replace(1, 0, "fly"))),
            answer(listOf(replace(1, 1, "fly"))),
        )
        val harness = harness(provider)

        val outcome = harness.editor.edit(request(), Language.ENGLISH, catalog) as AiPlanEditOutcome.Proposal

        assertEquals(2, outcome.attempts)
        assertNull(provider.calls.first().second)
        assertEquals(
            AiWorkoutRetryIssueKind.CONTRACT to "unknown_position",
            provider.calls.last().second!!.issues.single().let { it.kind to it.code },
        )
    }

    @Test
    fun `a change the validator rejects also gets the repair attempt`() = runTest {
        val provider = SequencedEditProvider(
            answer(listOf(replace(1, 1, "barbell-row"))),
            answer(listOf(replace(1, 1, "fly"))),
        )
        val harness = harness(provider)

        val outcome = harness.editor.edit(request(), Language.ENGLISH, catalog) as AiPlanEditOutcome.Proposal

        assertEquals(2, outcome.attempts)
        assertEquals("exercise_not_offered", provider.calls.last().second!!.issues.single().code)
    }

    @Test
    fun `still invalid after repair is INVALID_RESPONSE`() = runTest {
        val bad = answer(listOf(replace(1, 0, "fly")))
        val harness = harness(SequencedEditProvider(bad, bad))

        val outcome = harness.editor.edit(request(), Language.ENGLISH, catalog) as AiPlanEditOutcome.Failure

        assertEquals(AiGenerationFailureReason.INVALID_RESPONSE, outcome.failure.reason)
        assertEquals(2, outcome.failure.attempts)
    }

    @Test
    fun `malformed output gets the repair attempt`() = runTest {
        val provider = SequencedEditProvider(
            ProviderPlanEditResult.Failed(ProviderFailure.FORMAT),
            answer(listOf(replace(1, 1, "fly"))),
        )
        val harness = harness(provider)

        val outcome = harness.editor.edit(request(), Language.ENGLISH, catalog) as AiPlanEditOutcome.Proposal

        assertEquals(2, outcome.attempts)
        assertEquals(AiWorkoutRetryFeedback.Malformed, provider.calls.last().second)
    }

    @Test
    fun `a provider failure is reported with its detail`() = runTest {
        val harness = harness(SequencedEditProvider(ProviderPlanEditResult.Failed(ProviderFailure.QUOTA, "slow down")))

        val outcome = harness.editor.edit(request(), Language.ENGLISH, catalog) as AiPlanEditOutcome.Failure

        assertEquals(AiGenerationFailureReason.PROVIDER_FAILURE, outcome.failure.reason)
        assertEquals(ProviderFailure.QUOTA, outcome.failure.providerFailure)
        assertEquals("slow down", outcome.failure.detail)
    }

    @Test
    fun `the candidates offered are the equipment-filtered catalog`() = runTest {
        val provider = SequencedEditProvider(answer(emptyList()))
        val harness = harness(provider)

        harness.editor.edit(request(), Language.ENGLISH, catalog)

        val offered = provider.requests.single().candidates.map { it.id }
        assertTrue("press" in offered)
        assertTrue("barbell-row" !in offered)
    }

    @Test
    fun `a timeout reports the configured deadline`() = runTest {
        val harness = harness(SequencedEditProvider(ProviderPlanEditResult.Failed(ProviderFailure.TIMEOUT)))
        harness.repository.setRequestTimeoutSeconds(25)

        val outcome = harness.editor.edit(request(), Language.ENGLISH, catalog) as AiPlanEditOutcome.Failure

        assertEquals(25, outcome.failure.deadlineSeconds)
    }

    private suspend fun harness(provider: AiProvider, configured: Boolean = true): Harness {
        val repository = ProviderRepository(
            ProviderSettingsDataSource(FakePreferencesStore()),
            InMemorySecretStore(),
        )
        if (configured) repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")
        return Harness(repository, AiPlanEditor(repository, mapOf(provider.id to provider)))
    }

    private fun request() = PlanEditRequest(
        profile = UserProfile(
            id = "private-profile",
            goal = TrainingGoal.HYPERTROPHY,
            experience = ExperienceLevel.INTERMEDIATE,
            trainingDaysPerWeek = 3,
            sessionLengthMs = 60 * 60_000L,
            availableEquipment = setOf(Equipment.BODY_WEIGHT),
        ),
        plan = plan,
        instruction = "swap the press",
        focus = PlanRef(1, 1),
    )

    private fun candidate(id: String, equipment: Equipment = Equipment.BODY_WEIGHT) = ExerciseCandidate(
        id = ExerciseId(id),
        name = id,
        bodyPart = BodyPart.CHEST,
        target = Muscle.PECTORALS,
        muscleGroup = Muscle.PECTORALS,
        secondaryMuscles = emptySet(),
        equipment = equipment,
    )

    private fun replace(day: Int, position: Int, id: String) =
        AiPlanChange(PlanEditOp.REPLACE, day, position, id, 3, 10, null, null, 60)

    private fun answer(changes: List<AiPlanChange>, rationale: String = "Because.") =
        ProviderPlanEditResult.Ok(AiPlanEditResponse(changes, rationale))

    private data class Harness(val repository: ProviderRepository, val editor: AiPlanEditor)

    private class SequencedEditProvider(vararg results: ProviderPlanEditResult) : AiProvider {
        override val id = ProviderId.GEMINI
        private val remaining = results.toMutableList()
        val calls = mutableListOf<Pair<ProviderConfig, AiWorkoutRetryFeedback?>>()
        val requests = mutableListOf<AiPlanEditRequest>()

        override suspend fun testConnection(config: ProviderConfig) = ProviderTestResult.Ok(modelConfirmed = true)

        override suspend fun generateWorkout(
            config: ProviderConfig,
            request: AiWorkoutRequest,
            retryFeedback: AiWorkoutRetryFeedback?,
        ): ProviderGenerationResult = error("An edit must not generate")

        override suspend fun editPlan(
            config: ProviderConfig,
            request: AiPlanEditRequest,
            retryFeedback: AiWorkoutRetryFeedback?,
        ): ProviderPlanEditResult {
            calls += config to retryFeedback
            requests += request
            return remaining.removeAt(0)
        }
    }
}
