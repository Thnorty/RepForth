package com.repforth.core.media.manifest

import android.content.Context
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.MediaRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Access to the exercise media manifest (§6, §9).
 *
 * Provides lookups for thumbnails and GIFs by [ExerciseId]. In builds where the
 * manifest is not bundled (`placeholder`), returns [MediaRef.Unavailable]
 * gracefully without network or I/O errors.
 */
interface MediaManifestRepository {
    suspend fun getManifest(): MediaManifest?
    suspend fun findThumbnail(exerciseId: ExerciseId): MediaRef
    suspend fun findAnimation(exerciseId: ExerciseId): MediaRef
}

@Singleton
class AssetMediaManifestRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaManifestRepository {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cachedManifest: MediaManifest? = null
    private var cachedThumbnails: Map<String, MediaRef>? = null
    private var cachedAnimations: Map<String, MediaRef>? = null
    private var loadAttempted = false

    override suspend fun getManifest(): MediaManifest? = withContext(ioDispatcher) {
        ensureLoaded()
        cachedManifest
    }

    override suspend fun findThumbnail(exerciseId: ExerciseId): MediaRef = withContext(ioDispatcher) {
        ensureLoaded()
        cachedThumbnails?.get(exerciseId.value) ?: MediaRef.Unavailable
    }

    override suspend fun findAnimation(exerciseId: ExerciseId): MediaRef = withContext(ioDispatcher) {
        ensureLoaded()
        cachedAnimations?.get(exerciseId.value) ?: MediaRef.Unavailable
    }

    private suspend fun ensureLoaded() {
        if (loadAttempted) return
        mutex.withLock {
            if (loadAttempted) return
            try {
                val inputStream: InputStream? = try {
                    context.assets.open(MANIFEST_ASSET_NAME)
                } catch (e: IOException) {
                    null
                }

                if (inputStream != null) {
                    val content = inputStream.bufferedReader().use { it.readText() }
                    val manifest = json.decodeFromString<MediaManifest>(content)
                    cachedManifest = manifest

                    val baseUrl = manifest.baseUrl.trimEnd('/') + "/"
                    val thumbs = HashMap<String, MediaRef>(manifest.entries.size)
                    val anims = HashMap<String, MediaRef>(manifest.entries.size)

                    for (entry in manifest.entries) {
                        thumbs[entry.id] = MediaRef(
                            url = baseUrl + entry.thumbnail.path,
                            sha256 = entry.thumbnail.sha256,
                            byteSize = entry.thumbnail.bytes,
                        )
                        anims[entry.id] = MediaRef(
                            url = baseUrl + entry.animation.path,
                            sha256 = entry.animation.sha256,
                            byteSize = entry.animation.bytes,
                        )
                    }

                    cachedThumbnails = thumbs
                    cachedAnimations = anims
                }
            } catch (_: Exception) {
                // If parsing fails or asset missing, leave caches empty so lookups safely yield Unavailable
            } finally {
                loadAttempted = true
            }
        }
    }

    companion object {
        const val MANIFEST_ASSET_NAME = "media-manifest.json"
    }
}
