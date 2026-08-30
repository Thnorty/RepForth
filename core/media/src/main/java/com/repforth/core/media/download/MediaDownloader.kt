package com.repforth.core.media.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.media.cache.MediaCacheManager
import com.repforth.core.model.MediaRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Thrown when downloaded media bytes do not match the expected SHA-256 or byte count (§6, §9).
 */
class MediaIntegrityException(message: String) : IOException(message)

/**
 * Thrown when network policy (e.g. Wi-Fi only) prevents a download.
 */
class MediaNetworkPolicyException(message: String) : IOException(message)

/**
 * Downloads media on demand and validates its SHA-256 hash before promoting to the durable cache.
 */
@Singleton
class MediaDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val cacheManager: MediaCacheManager,
    private val preferencesDataSource: UserPreferencesDataSource,
) {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Downloads and validates media for an exercise asset.
     *
     * Returns the cached [File] on success.
     */
    suspend fun download(
        mediaVersion: Int,
        exerciseId: String,
        mediaType: String,
        mediaRef: MediaRef,
        forceAllowCellular: Boolean = false,
    ): Result<File> = withContext(ioDispatcher) {
        val url = mediaRef.url ?: return@withContext Result.failure(
            IllegalArgumentException("MediaRef url is null for $exerciseId")
        )
        val expectedSha = mediaRef.sha256 ?: return@withContext Result.failure(
            IllegalArgumentException("MediaRef sha256 is null for $exerciseId")
        )
        val expectedBytes = mediaRef.byteSize

        val targetFile = cacheManager.cacheFileFor(mediaVersion, exerciseId, mediaType, expectedSha)
        if (targetFile.exists() && targetFile.length() > 0) {
            cacheManager.touch(targetFile)
            return@withContext Result.success(targetFile)
        }

        if (!forceAllowCellular) {
            val prefs = preferencesDataSource.preferences.first()
            if (prefs.mediaWifiOnly && !isOnWifi()) {
                return@withContext Result.failure(
                    MediaNetworkPolicyException("Media download requires Wi-Fi per user preferences")
                )
            }
        }

        val tempFile = File(targetFile.parentFile, "${expectedSha}.tmp")

        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to download media: HTTP ${response.code}")
                    )
                }

                val body = response.body ?: return@withContext Result.failure(
                    IOException("Response body is null")
                )

                val digest = MessageDigest.getInstance("SHA-256")
                var totalBytesRead = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            totalBytesRead += read
                        }
                        output.flush()
                    }
                }

                if (expectedBytes != null && totalBytesRead != expectedBytes) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        MediaIntegrityException(
                            "Byte size mismatch for $exerciseId: expected $expectedBytes, got $totalBytesRead"
                        )
                    )
                }

                val computedSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (!computedSha.equals(expectedSha, ignoreCase = true)) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        MediaIntegrityException(
                            "SHA-256 mismatch for $exerciseId: expected $expectedSha, computed $computedSha"
                        )
                    )
                }

                if (tempFile.renameTo(targetFile)) {
                    cacheManager.calculateCacheSize()
                    cacheManager.evictLruIfNeeded(MediaCacheManager.DEFAULT_MAX_CACHE_BYTES)
                    Result.success(targetFile)
                } else {
                    tempFile.delete()
                    Result.failure(IOException("Failed to move temporary media file to cache destination"))
                }
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Result.failure(e)
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
