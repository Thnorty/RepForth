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
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.rules.RulesEngine
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.testing.InMemorySecretStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkoutGeneratorTest {

    @Test
    fun `provider receives only candidates that passed the local hard rules`() = runTest {
        val provider = SequencedProvider(valid("press"))
        val harness = harness(provider)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        val outcome = harness.generator.generate(
            request = request(targetMuscles = setOf(Muscle.PECTORALS)),
            locale = Language.TURKISH,
            candidates = listOf(candidate("row", Muscle.LATS), candidate("press", Muscle.PECTORALS)),
            planName = "Test plan",
        ) as AiWorkoutGenerationOutcome.Provider

        assertEquals(1, outcome.attempts)
        assertEquals(listOf("press"), provider.calls.single().request.candidateExercises.map { it.id })
        assertNull(provider.calls.single().retryFeedback)
    }

    @Test
    fun `invalid output is retried once with typed validation feedback`() = runTest {
        val provider = SequencedProvider(
            valid("not-offered"),
            valid("press"),
        )
        val harness = harness(provider)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        val outcome = harness.generator.generate(
            request(),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
            "Test plan",
        ) as AiWorkoutGenerationOutcome.Provider

        assertEquals(2, outcome.attempts)
        assertNull(provider.calls.first().retryFeedback)
        assertEquals(
            AiWorkoutRetryIssue(
                AiWorkoutRetryIssueKind.CONTRACT,
                "exercise_not_offered",
                "not-offered",
            ),
            provider.calls.last().retryFeedback!!.issues.single(),
        )
    }

    @Test
    fun `malformed structured output gets the same single repair opportunity`() = runTest {
        val provider = SequencedProvider(
            ProviderGenerationResult.Failed(ProviderFailure.FORMAT),
            valid("press"),
        )
        val harness = harness(provider)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        val outcome = harness.generator.generate(
            request(),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
            "Test plan",
        ) as AiWorkoutGenerationOutcome.Provider

        assertEquals(2, outcome.attempts)
        assertEquals(AiWorkoutRetryFeedback.Malformed, provider.calls.last().retryFeedback)
    }

    @Test
    fun `a second invalid answer falls back to the deterministic rules result`() = runTest {
        val invalid = valid("not-offered")
        val provider = SequencedProvider(invalid, invalid)
        val harness = harness(provider)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")
        val request = request(seed = 42)
        val candidates = listOf(candidate("press", Muscle.PECTORALS))

        val outcome = harness.generator.generate(
            request,
            Language.ENGLISH,
            candidates,
            "Test plan",
        ) as AiWorkoutGenerationOutcome.Rules

        assertEquals(AiFallbackReason.INVALID_RESPONSE, outcome.reason)
        assertEquals(2, outcome.attempts)
        assertEquals(
            RulesEngine().generate(request, candidates, "Test plan"),
            outcome.generation,
        )
    }

    @Test
    fun `format failure after repair becomes invalid-response fallback`() = runTest {
        val provider = SequencedProvider(
            ProviderGenerationResult.Failed(ProviderFailure.FORMAT),
            ProviderGenerationResult.Failed(ProviderFailure.FORMAT),
        )
        val harness = harness(provider)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        val outcome = harness.generator.generate(
            request(),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
            "Test plan",
        ) as AiWorkoutGenerationOutcome.Rules

        assertEquals(AiFallbackReason.INVALID_RESPONSE, outcome.reason)
        assertEquals(ProviderFailure.FORMAT, outcome.providerFailure)
        assertEquals(2, outcome.attempts)
    }

    @Test
    fun `actionable provider failures fall back immediately without a billable retry`() = runTest {
        ProviderFailure.entries.filterNot { it == ProviderFailure.FORMAT }.forEach { failure ->
            val provider = SequencedProvider(ProviderGenerationResult.Failed(failure))
            val harness = harness(provider)
            harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

            val outcome = harness.generator.generate(
                request(),
                Language.ENGLISH,
                listOf(candidate("press", Muscle.PECTORALS)),
                "Test plan",
            ) as AiWorkoutGenerationOutcome.Rules

            assertEquals(failure, outcome.providerFailure)
            assertEquals(AiFallbackReason.PROVIDER_FAILURE, outcome.reason)
            assertEquals(1, outcome.attempts)
            assertEquals(1, provider.calls.size)
        }
    }

    @Test
    fun `missing key falls back before an adapter is called`() = runTest {
        val provider = SequencedProvider(valid("press"))
        val harness = harness(provider)

        val outcome = harness.generator.generate(
            request(),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
            "Test plan",
        ) as AiWorkoutGenerationOutcome.Rules

        assertEquals(AiFallbackReason.NO_PROVIDER_CONFIGURATION, outcome.reason)
        assertEquals(0, outcome.attempts)
        assertTrue(provider.calls.isEmpty())
    }

    @Test
    fun `missing adapter is a local fallback rather than an exception`() = runTest {
        val harness = harness(provider = null)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        val outcome = harness.generator.generate(
            request(),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
            "Test plan",
        ) as AiWorkoutGenerationOutcome.Rules

        assertEquals(AiFallbackReason.NO_PROVIDER_ADAPTER, outcome.reason)
        assertEquals(0, outcome.attempts)
    }

    @Test
    fun `empty local filter never resolves or calls a provider`() = runTest {
        val provider = SequencedProvider(valid("row"))
        val harness = harness(provider)

        val outcome = harness.generator.generate(
            request(targetMuscles = setOf(Muscle.PECTORALS)),
            Language.ENGLISH,
            listOf(candidate("row", Muscle.LATS)),
            "Test plan",
        ) as AiWorkoutGenerationOutcome.Rules

        assertEquals(AiFallbackReason.NO_ELIGIBLE_CANDIDATES, outcome.reason)
        assertEquals(0, outcome.attempts)
        assertTrue(provider.calls.isEmpty())
        assertNull(outcome.generation.plan)
    }

    private fun harness(provider: AiProvider?): Harness {
        val repository = ProviderRepository(
            ProviderSettingsDataSource(FakePreferencesStore()),
            InMemorySecretStore(),
        )
        return Harness(
            repository = repository,
            generator = AiWorkoutGenerator(
                repository,
                provider?.let { mapOf(it.id to it) }.orEmpty(),
            ),
        )
    }

    private fun request(
        targetMuscles: Set<Muscle> = setOf(Muscle.PECTORALS),
        seed: Long = 0,
    ) = GenerationRequest(
        profile = UserProfile(
            id = "private-profile",
            goal = TrainingGoal.HYPERTROPHY,
            experience = ExperienceLevel.INTERMEDIATE,
            trainingDaysPerWeek = 3,
            sessionLengthMs = 60 * 60_000L,
            availableEquipment = emptySet(),
            preferredMuscles = emptySet(),
            exclusions = emptySet(),
        ),
        targetMuscles = targetMuscles,
        seed = seed,
    )

    private fun candidate(id: String, muscle: Muscle) = ExerciseCandidate(
        id = ExerciseId(id),
        name = "local name $id",
        bodyPart = BodyPart.CHEST,
        target = muscle,
        muscleGroup = muscle,
        secondaryMuscles = emptySet(),
        equipment = Equipment.DUMBBELL,
    )

    private fun valid(id: String) = ProviderGenerationResult.Ok(
        AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            exercises = listOf(
                AiPlannedExercise(
                    exerciseId = id,
                    order = 0,
                    sets = 3,
                    repetitions = 10,
                    restSeconds = 60,
                ),
            ),
            rationale = "Balanced volume",
        ),
    )

    private data class Harness(
        val repository: ProviderRepository,
        val generator: AiWorkoutGenerator,
    )

    private data class ProviderCall(
        val config: ProviderConfig,
        val request: AiWorkoutRequest,
        val retryFeedback: AiWorkoutRetryFeedback?,
    )

    private class SequencedProvider(
        vararg results: ProviderGenerationResult,
    ) : AiProvider {
        override val id = ProviderId.GEMINI
        private val remaining = results.toMutableList()
        val calls = mutableListOf<ProviderCall>()

        override suspend fun testConnection(config: ProviderConfig) =
            ProviderTestResult.Ok(modelConfirmed = true)

        override suspend fun generateWorkout(
            config: ProviderConfig,
            request: AiWorkoutRequest,
            retryFeedback: AiWorkoutRetryFeedback?,
        ): ProviderGenerationResult {
            calls += ProviderCall(config, request, retryFeedback)
            return remaining.removeAt(0)
        }
    }
}
