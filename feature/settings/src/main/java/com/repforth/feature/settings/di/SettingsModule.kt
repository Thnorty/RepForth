package com.repforth.feature.settings.di

import android.content.ContentResolver
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * The one Android dependency this feature has.
 *
 * Provided rather than reached for through a Context inside the ViewModel: a
 * `ContentResolver` is the seam where a chosen file becomes bytes, and passing
 * it in keeps the ViewModel testable with a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object SettingsModule {

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver
}
