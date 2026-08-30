package com.repforth.core.media

import com.repforth.core.media.cache.MediaCacheManager
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaCacheManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var tempDir: File
    private lateinit var cacheManager: MediaCacheManager

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "repforth_cache_test_${System.nanoTime()}")
        tempDir.mkdirs()
        cacheManager = MediaCacheManager(tempDir, dispatcher)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `cache file path adheres to section 9 key hierarchy`() {
        val file = cacheManager.cacheFileFor(
            mediaVersion = 1,
            exerciseId = "0001",
            mediaType = "thumbnail",
            sha256 = "52b897152a76309a61be8bc917196bfe3558e7d55861f2d44b8375e4d5720286",
        )
        val expectedRelative = "1/0001/thumbnail/52b897152a76309a61be8bc917196bfe3558e7d55861f2d44b8375e4d5720286.bin"
        assertEquals(
            File(tempDir, expectedRelative).absolutePath,
            file.absolutePath,
        )
    }

    @Test
    fun `isCached detects existing non-empty file`() = runTest(dispatcher) {
        val file = cacheManager.cacheFileFor(1, "0001", "thumb", "sha123")
        assertFalse(cacheManager.isCached(1, "0001", "thumb", "sha123"))

        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertTrue(cacheManager.isCached(1, "0001", "thumb", "sha123"))
    }

    @Test
    fun `calculateCacheSize aggregates byte sizes and clearCache resets it`() = runTest(dispatcher) {
        val file1 = cacheManager.cacheFileFor(1, "0001", "thumb", "sha1")
        file1.writeBytes(ByteArray(100))
        val file2 = cacheManager.cacheFileFor(1, "0002", "thumb", "sha2")
        file2.writeBytes(ByteArray(250))

        val size = cacheManager.calculateCacheSize()
        assertEquals(350L, size)

        val cleared = cacheManager.clearCache()
        assertTrue(cleared)
        assertEquals(0L, cacheManager.cacheSize.first())
        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }

    @Test
    fun `evictLru removes oldest accessed files when cap exceeded`() = runTest(dispatcher) {
        val fileOld = cacheManager.cacheFileFor(1, "0001", "thumb", "sha_old")
        fileOld.writeBytes(ByteArray(500))
        fileOld.setLastModified(1000L)

        val fileNew = cacheManager.cacheFileFor(1, "0002", "thumb", "sha_new")
        fileNew.writeBytes(ByteArray(500))
        fileNew.setLastModified(5000L)

        // Evict down to max 600 bytes -> should evict fileOld (500b), leaving fileNew (500b)
        cacheManager.evictLruIfNeeded(maxSizeBytes = 600L)

        assertFalse("Old file should be evicted", fileOld.exists())
        assertTrue("New file should remain", fileNew.exists())
        assertEquals(500L, cacheManager.cacheSize.first())
    }
}
