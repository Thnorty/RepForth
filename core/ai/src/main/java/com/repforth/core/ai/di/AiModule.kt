package com.repforth.core.ai.di

import com.repforth.core.ai.AiProvider
import com.repforth.core.ai.AiWorkoutGenerationService
import com.repforth.core.ai.AiWorkoutGenerator
import com.repforth.core.ai.GeminiProvider
import com.repforth.core.ai.OpenAiCompatibleProvider
import com.repforth.core.ai.http.ProviderHttp
import com.repforth.core.model.ProviderId
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * The HTTP stack the provider adapters share.
 *
 * One [OkHttpClient] for the process. Its connection pool, dispatcher and
 * thread pool are the expensive parts, and a client per request throws all
 * three away — the per-call timeout is applied with `newBuilder()` in
 * [ProviderHttp], which copies the configuration and keeps the pool.
 *
 * No logging interceptor, in any build. §8 point 5 asks for authorization
 * headers and prompts to be redacted from release logs, and the cheapest way to
 * honour that is to have nothing that could print them — a debug-only
 * interceptor is one `buildTypes` edit away from being everywhere.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AiModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // Providers add fields without warning, and a response this app cannot
        // parse is a feature that stops working for a reason nobody can see.
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Connect and read are bounded separately from the call timeout so a
        // server that accepts the connection and then says nothing still fails
        // in a bounded time.
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Off. A retry sends the request again, and "again" for a generation
        // request means the user is billed twice for one tap.
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    fun provideProviderHttp(client: OkHttpClient): ProviderHttp = ProviderHttp(client)

    /**
     * Every adapter, by the provider it speaks for.
     *
     * A plain map rather than Dagger multibindings: there are two entries, both
     * are constructed here, and `@IntoMap` with a map key annotation would be
     * more ceremony than the thing it configures.
     */
    @Provides
    @Singleton
    fun provideProviders(http: ProviderHttp, json: Json): Map<ProviderId, AiProvider> = mapOf(
        // The endpoint is the default; nothing outside a test supplies another.
        ProviderId.GEMINI to GeminiProvider(http, json),
        ProviderId.OPENAI_COMPATIBLE to OpenAiCompatibleProvider(http, json),
    )

    @Provides
    fun provideWorkoutGenerationService(
        generator: AiWorkoutGenerator,
    ): AiWorkoutGenerationService = generator

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 60L
}
