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
        ) as AiWorkoutGenerationOutcome.Provider

        assertEquals(1, outcome.attempts)
        assertEquals(listOf("press"), provider.calls.single().request.candidates.map { it.id })
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
            request(days = 1),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
        ) as AiWorkoutGenerationOutcome.Provider

        assertEquals(2, outcome.attempts)
        assertNull(provider.calls.first().retryFeedback)
        assertEquals(
            AiWorkoutRetryIssueKind.CONTRACT to "exercise_not_offered",
            provider.calls.last().retryFeedback!!.issues.single().let { it.kind to it.code },
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
            request(days = 1),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
        ) as AiWorkoutGenerationOutcome.Provider

        assertEquals(2, outcome.attempts)
        assertEquals(AiWorkoutRetryFeedback.Malformed, provider.calls.last().retryFeedback)
    }

    @Test
    fun `a second invalid answer produces failure outcome`() = runTest {
        val invalid = valid("not-offered")
        val provider = SequencedProvider(invalid, invalid)
        val harness = harness(provider)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")
        val request = request(days = 1)
        val candidates = listOf(candidate("press", Muscle.PECTORALS))

        val outcome = harness.generator.generate(
            request,
            Language.ENGLISH,
            candidates,
        ) as AiWorkoutGenerationOutcome.Failure

        assertEquals(AiGenerationFailureReason.INVALID_RESPONSE, outcome.reason)
        assertEquals(2, outcome.attempts)
    }

    @Test
    fun `format failure after repair becomes invalid-response failure`() = runTest {
        val provider = SequencedProvider(
            ProviderGenerationResult.Failed(ProviderFailure.FORMAT),
            ProviderGenerationResult.Failed(ProviderFailure.FORMAT),
        )
        val harness = harness(provider)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        val outcome = harness.generator.generate(
            request(days = 1),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
        ) as AiWorkoutGenerationOutcome.Failure

        assertEquals(AiGenerationFailureReason.INVALID_RESPONSE, outcome.reason)
        assertEquals(ProviderFailure.FORMAT, outcome.providerFailure)
        assertEquals(2, outcome.attempts)
    }

    @Test
    fun `actionable provider failures fail immediately without a billable retry`() = runTest {
        // SERVER is excluded deliberately, and the exclusion is the point of
        // the two tests below: a 5xx is not a billable retry, because the
        // provider produced nothing to bill for. FORMAT is excluded because it
        // is the repair case §8 allows.
        ProviderFailure.entries
            .filterNot { it == ProviderFailure.FORMAT || it == ProviderFailure.SERVER }
            .forEach { failure ->
            val provider = SequencedProvider(ProviderGenerationResult.Failed(failure))
            val harness = harness(provider)
            harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

            val outcome = harness.generator.generate(
                request(days = 1),
                Language.ENGLISH,
                listOf(candidate("press", Muscle.PECTORALS)),
            ) as AiWorkoutGenerationOutcome.Failure

            assertEquals(failure, outcome.providerFailure)
            assertEquals(AiGenerationFailureReason.PROVIDER_FAILURE, outcome.reason)
            assertEquals(1, outcome.attempts)
            assertEquals(1, provider.calls.size)
        }
    }

    @Test
    fun `missing key fails before an adapter is called`() = runTest {
        val provider = SequencedProvider(valid("press"))
        val harness = harness(provider)

        val outcome = harness.generator.generate(
            request(days = 1),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
        ) as AiWorkoutGenerationOutcome.Failure

        assertEquals(AiGenerationFailureReason.NO_PROVIDER_CONFIGURATION, outcome.reason)
        assertEquals(0, outcome.attempts)
        assertTrue(provider.calls.isEmpty())
    }

    @Test
    fun `missing adapter is a failure outcome rather than an exception`() = runTest {
        val harness = harness(provider = null)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        val outcome = harness.generator.generate(
            request(days = 1),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
        ) as AiWorkoutGenerationOutcome.Failure

        assertEquals(AiGenerationFailureReason.NO_PROVIDER_ADAPTER, outcome.reason)
        assertEquals(0, outcome.attempts)
    }

    @Test
    fun `empty local filter never resolves or calls a provider`() = runTest {
        val provider = SequencedProvider(valid("row"))
        val harness = harness(provider)

        val outcome = harness.generator.generate(
            request(days = 1, targetMuscles = setOf(Muscle.PECTORALS)),
            Language.ENGLISH,
            listOf(candidate("row", Muscle.LATS)),
        ) as AiWorkoutGenerationOutcome.Failure

        assertEquals(AiGenerationFailureReason.NO_ELIGIBLE_CANDIDATES, outcome.reason)
        assertEquals(0, outcome.attempts)
        assertTrue(provider.calls.isEmpty())
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
        days: Int = 1,
        targetMuscles: Set<Muscle> = setOf(Muscle.PECTORALS),
    ) = GenerationRequest(
        profile = UserProfile(
            id = "private-profile",
            goal = TrainingGoal.HYPERTROPHY,
            experience = ExperienceLevel.INTERMEDIATE,
            trainingDaysPerWeek = days,
            sessionLengthMs = 60 * 60_000L,
            availableEquipment = emptySet(),
            preferredMuscles = emptySet(),
            exclusions = emptySet(),
        ),
        targetMuscles = targetMuscles,
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
            days = listOf(
                AiPlannedDay(
                    title = "Push",
                    exercises = listOf(
                        AiPlannedExercise(
                            exerciseId = id,
                            sets = 3,
                            repetitions = 10,
                            restSeconds = 60,
                        ),
                    ),
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

    /**
     * A demand spike must not end the generation.
     *
     * This is the failure a real device actually hit: Gemini answered
     * `503 UNAVAILABLE` with "spikes in demand are usually temporary", the app
     * gave up on the first one, and the user was told the coach could not build
     * a plan — which pointed at their constraints rather than at Google.
     */
    @Test
    fun `a transient server failure is retried and can still succeed`() = runTest {
        val provider = SequencedProvider(
            ProviderGenerationResult.Failed(ProviderFailure.SERVER),
            valid("press"),
        )
        val harness = harness(provider)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        val outcome = harness.generator.generate(
            request(days = 1),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
        )

        assertTrue(
            "A 503 followed by a good answer must produce a plan, not a failure",
            outcome is AiWorkoutGenerationOutcome.Provider,
        )
        assertEquals(2, provider.calls.size)
        // The second call is the same request, not a repair: there was no
        // output to correct, so sending repair feedback would describe a
        // mistake the model never made.
        assertEquals(null, provider.calls.last().retryFeedback)
        assertEquals(1, (outcome as AiWorkoutGenerationOutcome.Provider).attempts)
    }

    @Test
    fun `a provider that stays down gives up after the bounded attempts`() = runTest {
        val provider = SequencedProvider(
            ProviderGenerationResult.Failed(ProviderFailure.SERVER),
            ProviderGenerationResult.Failed(ProviderFailure.SERVER),
            ProviderGenerationResult.Failed(ProviderFailure.SERVER),
        )
        val harness = harness(provider)
        harness.repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        val outcome = harness.generator.generate(
            request(days = 1),
            Language.ENGLISH,
            listOf(candidate("press", Muscle.PECTORALS)),
        ) as AiWorkoutGenerationOutcome.Failure

        assertEquals(ProviderFailure.SERVER, outcome.providerFailure)
        assertEquals(AiGenerationFailureReason.PROVIDER_FAILURE, outcome.reason)
        assertEquals(
            "Retrying must be bounded; an app that hammers an overloaded " +
                "provider is part of the problem",
            3,
            provider.calls.size,
        )
    }

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
