package com.repforth.app

import com.repforth.core.ai.AiPlannedDay
import com.repforth.core.ai.AiPlannedExercise
import com.repforth.core.ai.AiWorkoutGenerationOutcome
import com.repforth.core.ai.AiWorkoutGenerationService
import com.repforth.core.ai.AiWorkoutResponse
import com.repforth.core.ai.ProviderAvailability
import com.repforth.core.ai.di.AiGenerationModule
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.Language
import com.repforth.core.rules.GenerationRequest
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Coach answers from a fixture on the device, because it cannot answer at all.
 *
 * The real `AiWorkoutGenerator` reads a stored provider configuration and
 * returns `NO_PROVIDER_CONFIGURATION` when there is none. On the managed
 * emulator there never is one, and §20 forbids putting a key where a test could
 * find it — so "Build it" on a test device can only ever fail, and every screen
 * behind it was untestable. `coachFillsTheBuilderButSavesNothingUntilAsked`
 * waited fifteen seconds for a plan that no configuration could have produced.
 *
 * This replaces the one binding that chooses the implementation, and nothing
 * else: the HTTP client, the JSON codec and the provider adapters are still the
 * production ones, and are still unreachable, because nothing calls them.
 *
 * **One day, not three.** A single-day answer is stored as a standalone workout
 * rather than a week, which is the shape the builder screen edits — the same
 * screen the manual path uses. The week path has its own screen and its own
 * tests; making the fixture multi-day here would test the accordion instead of
 * the thing this file is for, which is that a generated draft is a draft.
 *
 * No network is reached and no key is read, which is the point.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AiGenerationModule::class],
)
object TestGenerationModule {

    @Provides
    @Singleton
    fun provideWorkoutGenerationService(): AiWorkoutGenerationService =
        FixtureGenerationService

    /**
     * And the same answer to "is a provider set up?".
     *
     * Coach now says so before the form rather than failing after it, which
     * means the screen would offer a notice and a disabled button on a device
     * that has no provider -- every device this runs on. Replaced here for the
     * same reason and in the same breath as the generator: this module stands
     * in for the whole of `AiGenerationModule`, so leaving it out would not
     * merely change behaviour, it would leave the binding missing.
     */
    @Provides
    @Singleton
    fun provideProviderAvailability(): ProviderAvailability = FixtureAvailability

    /** Ids from the pinned catalog, so the drafts resolve to real names. */
    const val FIRST_EXERCISE_ID = "0025"
    const val SECOND_EXERCISE_ID = "0032"
}

private object FixtureAvailability : ProviderAvailability {
    override val configured: Flow<Boolean> = flowOf(true)
}

private object FixtureGenerationService : AiWorkoutGenerationService {

    override suspend fun generate(
        request: GenerationRequest,
        locale: Language,
        candidates: List<ExerciseCandidate>,
    ): AiWorkoutGenerationOutcome = AiWorkoutGenerationOutcome.Provider(
        response = AiWorkoutResponse(
            days = listOf(
                AiPlannedDay(
                    // Blank, so the view model fills it from the translated
                    // day titles the screen passes down -- the production path.
                    title = "",
                    exercises = listOf(
                        AiPlannedExercise(
                            exerciseId = TestGenerationModule.FIRST_EXERCISE_ID,
                            sets = 3,
                            repetitions = 10,
                            restSeconds = 60,
                        ),
                        AiPlannedExercise(
                            exerciseId = TestGenerationModule.SECOND_EXERCISE_ID,
                            sets = 3,
                            repetitions = 12,
                            restSeconds = 60,
                        ),
                    ),
                ),
            ),
            rationale = "Fixture answer for instrumentation.",
        ),
        attempts = 1,
    )
}
