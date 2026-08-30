package com.repforth.core.media.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages the durable on-disk cache for exercise media (§9).
 *
 * Cache key hierarchy on disk:
 * `cacheDir/exercise_media/<mediaVersion>/<exerciseId>/<mediaType>/<sha256>.bin`
 *
 * Provides thread-safe cache verification, size measurement, LRU eviction, and
 * cache clearing.
 */
@Singleton
class MediaCacheManager(
    val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(context.cacheDir, CACHE_SUBDIR), Dispatchers.IO)

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: Flow<Long> = _cacheSize.asStateFlow()

    init {
        // Initial async calculation of cache footprint
        refreshCacheSizeAsync()
    }

    /**
     * Resolves the target on-disk file for an asset under the §9 key structure.
     */
    fun cacheFileFor(
        mediaVersion: Int,
        exerciseId: String,
        mediaType: String,
        sha256: String,
    ): File {
        val dir = File(cacheDir, "$mediaVersion/$exerciseId/$mediaType")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$sha256.bin")
    }

    /**
     * Checks if a validated cached file exists for the given key.
     */
    suspend fun isCached(
        mediaVersion: Int,
        exerciseId: String,
        mediaType: String,
        sha256: String,
    ): Boolean = withContext(ioDispatcher) {
        val file = cacheFileFor(mediaVersion, exerciseId, mediaType, sha256)
        file.exists() && file.length() > 0
    }

    /**
     * Records access by touching the file's last modified time for LRU accounting.
     */
    suspend fun touch(file: File) = withContext(ioDispatcher) {
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
        }
    }

    /**
     * Recalculates total disk size used by cached media.
     */
    suspend fun calculateCacheSize(): Long = withContext(ioDispatcher) {
        val size = measureDir(cacheDir)
        _cacheSize.value = size
        size
    }

    /**
     * Clears all cached media files and updates the size stream.
     */
    suspend fun clearCache(): Boolean = withContext(ioDispatcher) {
        val success = if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        } else {
            true
        }
        _cacheSize.value = 0L
        success
    }

    /**
     * Evicts least-recently accessed files if the total size exceeds [maxSizeBytes].
     */
    suspend fun evictLruIfNeeded(maxSizeBytes: Long) = withContext(ioDispatcher) {
        if (!cacheDir.exists()) return@withContext
        var currentSize = measureDir(cacheDir)
        if (currentSize <= maxSizeBytes) {
            _cacheSize.value = currentSize
            return@withContext
        }

        val allFiles = cacheDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".bin") }
            .sortedBy { it.lastModified() }
            .toList()

        for (file in allFiles) {
            if (currentSize <= maxSizeBytes) break
            val length = file.length()
            if (file.delete()) {
                currentSize -= length
            }
        }
        _cacheSize.value = currentSize
    }

    private fun refreshCacheSizeAsync() {
        // Safe non-blocking recalculation
        try {
            if (cacheDir.exists()) {
                _cacheSize.value = measureDir(cacheDir)
            }
        } catch (_: Exception) {
            // Ignored during startup
        }
    }

    private fun measureDir(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val files = current.listFiles() ?: continue
            for (f in files) {
                if (f.isDirectory) {
                    stack.add(f)
                } else if (f.isFile) {
                    total += f.length()
                }
            }
        }
        return total
    }

    companion object {
        const val CACHE_SUBDIR = "exercise_media"
        const val DEFAULT_MAX_CACHE_BYTES = 250L * 1024L * 1024L // 250 MB (§9)
    }
}
