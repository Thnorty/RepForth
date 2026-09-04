package com.repforth.core.ai.di

import com.repforth.core.ai.AiWorkoutGenerationService
import com.repforth.core.ai.AiWorkoutGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The one binding that says which implementation answers a generation request.
 *
 * Its own module, and public, so an instrumentation test can replace it with
 * `@TestInstallIn` and nothing else. [AiModule] is `internal` and holds the
 * HTTP stack, the JSON codec and the provider adapters — replacing that to get
 * at one binding would mean a test re-declaring OkHttp, which is both invisible
 * coupling and a second place for the client's timeouts to be described.
 *
 * **This exists because Coach cannot otherwise be tested on a device at all.**
 * [AiWorkoutGenerator] answers `NO_PROVIDER_CONFIGURATION` until a real
 * provider key is stored, and §20 forbids a key reaching source, CI or a test
 * fixture — so on an emulator with no key, "Build it" can only ever fail, and
 * the screen that renders a generated draft was unreachable by any test that
 * did not either ship a credential or make a network call. Swapping this one
 * binding for a fixture makes the draft reachable without either.
 *
 * The seam is only a seam. Production has exactly one implementation, and the
 * generator's own behaviour — retries, validation, repair — is tested directly
 * in `core:ai`, not through this.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiGenerationModule {

    @Provides
    fun provideWorkoutGenerationService(
        generator: AiWorkoutGenerator,
    ): AiWorkoutGenerationService = generator
}
