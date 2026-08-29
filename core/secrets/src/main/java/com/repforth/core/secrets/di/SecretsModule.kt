package com.repforth.core.secrets.di

import android.content.Context
import com.repforth.core.secrets.KeystoreSecretStore
import com.repforth.core.secrets.SecretStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/**
 * Binds the one [SecretStore] the app uses.
 *
 * A singleton because the Tink keyset is resolved once and cached: two stores
 * would each try to generate a master key on first use, and the loser's
 * ciphertext would stop decrypting.
 *
 * The dispatcher is passed rather than injected. This project has no qualifier
 * infrastructure for dispatchers and does not need one for a single call site —
 * the parameter exists so a test can supply its own, which is the only reason
 * it is a parameter at all.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecretsModule {

    @Provides
    @Singleton
    fun provideSecretStore(@ApplicationContext context: Context): SecretStore =
        KeystoreSecretStore(context, Dispatchers.IO)
}
