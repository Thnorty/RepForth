package com.repforth.core.media

import android.content.Context
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.media.cache.MediaCacheManager
import com.repforth.core.media.download.MediaDownloader
import com.repforth.core.media.download.MediaIntegrityException
import com.repforth.core.model.MediaRef
import com.repforth.core.testing.FakePreferencesStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaDownloaderTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var cacheManager: MediaCacheManager
    private lateinit var preferences: UserPreferencesDataSource
    private lateinit var downloader: MediaDownloader
    private val okHttpClient = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File(System.getProperty("java.io.tmpdir"), "repforth_download_test_${System.nanoTime()}")
        tempDir.mkdirs()
        cacheManager = MediaCacheManager(tempDir, dispatcher)
        preferences = UserPreferencesDataSource(FakePreferencesStore())
        // Construct downloader with disabled Wi-Fi requirement for unit tests
        downloader = MediaDownloader(
            context = TestDummyContext(),
            okHttpClient = okHttpClient,
            cacheManager = cacheManager,
            preferencesDataSource = preferences,
        )
    }

    @After
    fun tearDown() {
        server.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `valid media download matches hash and byte size and saves to durable cache`() = runTest(dispatcher) {
        val payload = "exercise_thumbnail_data_sample".toByteArray(Charsets.UTF_8)
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }

        val response = MockResponse.Builder()
            .code(200)
            .body(okio.Buffer().write(payload))
            .build()
        server.enqueue(response)

        val url = server.url("/images/0001.jpg").toString()
        val ref = MediaRef(url = url, sha256 = sha256, byteSize = payload.size.toLong())

        val result = downloader.download(
            mediaVersion = 1,
            exerciseId = "0001",
            mediaType = "thumbnail",
            mediaRef = ref,
            forceAllowCellular = true,
        )

        assertTrue("Download should succeed", result.isSuccess)
        val file = result.getOrThrow()
        assertTrue(file.exists())
        assertEquals(payload.size.toLong(), file.length())
        assertTrue(cacheManager.isCached(1, "0001", "thumbnail", sha256))
    }

    @Test
    fun `corrupted media download with sha256 mismatch is rejected and temp file is cleaned up`() = runTest(dispatcher) {
        val payload = "corrupted_or_tampered_bytes".toByteArray(Charsets.UTF_8)
        val differentExpectedSha = "0000000000000000000000000000000000000000000000000000000000000000"

        val response = MockResponse.Builder()
            .code(200)
            .body(okio.Buffer().write(payload))
            .build()
        server.enqueue(response)

        val url = server.url("/images/0002.jpg").toString()
        val ref = MediaRef(url = url, sha256 = differentExpectedSha, byteSize = payload.size.toLong())

        val result = downloader.download(
            mediaVersion = 1,
            exerciseId = "0002",
            mediaType = "thumbnail",
            mediaRef = ref,
            forceAllowCellular = true,
        )

        assertTrue("Download should fail on checksum mismatch", result.isFailure)
        assertTrue(result.exceptionOrNull() is MediaIntegrityException)

        val targetFile = cacheManager.cacheFileFor(1, "0002", "thumbnail", differentExpectedSha)
        assertFalse("Durable cache file should not exist on integrity failure", targetFile.exists())
    }
}

/**
 * Minimal stub context for pure JVM test runs without Android framework services.
 */
private class TestDummyContext : android.content.ContextWrapper(null)
