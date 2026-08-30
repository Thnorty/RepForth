package com.repforth.core.media.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.io.File
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * Creates the single app-wide Coil ImageLoader (§9).
 *
 * Configures bounded memory and disk caches, OkHttp network fetcher, and GIF
 * decoding support.
 */
object RepForthImageLoader {

    fun build(context: Context, okHttpClient: OkHttpClient): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AnimatedImageDecoder.Factory())
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { okHttpClient }
                    )
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "coil_disk_cache").toOkioPath())
                    .maxSizeBytes(100L * 1024L * 1024L)
                    .build()
            }
            .build()
    }
}
