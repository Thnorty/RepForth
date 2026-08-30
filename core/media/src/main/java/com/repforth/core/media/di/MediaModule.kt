package com.repforth.core.media.di

import android.content.Context
import coil3.ImageLoader
import com.repforth.core.media.ManifestMediaResolver
import com.repforth.core.media.MediaResolver
import com.repforth.core.media.image.RepForthImageLoader
import com.repforth.core.media.manifest.AssetMediaManifestRepository
import com.repforth.core.media.manifest.MediaManifestRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {

    @Binds
    @Singleton
    abstract fun bindMediaManifestRepository(
        impl: AssetMediaManifestRepository,
    ): MediaManifestRepository

    @Binds
    @Singleton
    abstract fun bindMediaResolver(
        impl: ManifestMediaResolver,
    ): MediaResolver

    companion object {
        @Provides
        @Singleton
        fun provideImageLoader(
            @ApplicationContext context: Context,
            okHttpClient: OkHttpClient,
        ): ImageLoader = RepForthImageLoader.build(context, okHttpClient)
    }
}
