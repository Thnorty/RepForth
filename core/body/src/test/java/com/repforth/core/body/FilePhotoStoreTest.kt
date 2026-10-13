package com.repforth.core.body

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Progress photos as stored: upright, no larger than they need to be, and
 * carrying nothing about where or with what they were taken.
 *
 * The location half is the privacy promise in PRIVACY.md, and it cannot be
 * checked on the Galaxy — Knox blocks `run-as`, so the stored file cannot be
 * pulled. This test is the evidence.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FilePhotoStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = FilePhotoStore(context, Dispatchers.Unconfined)
    private val photos = File(context.filesDir, "body-photos")

    private fun jpeg(width: Int, height: Int, configure: ExifInterface.() -> Unit = {}): File {
        val file = File.createTempFile("fixture", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.absolutePath).apply {
            configure()
            saveAttributes()
        }
        return file
    }

    private fun stored(result: PhotoImport): File {
        assertTrue("Expected a stored photo, got $result", result is PhotoImport.Stored)
        return store.fileFor((result as PhotoImport.Stored).fileName)
    }

    private fun size(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    @Test
    fun `location is stripped`() = runTest {
        val source = jpeg(400, 300) {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "41/1,0/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "28/1,58/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            setAttribute(ExifInterface.TAG_MAKE, "samsung")
        }
        assertEquals("Fixture must carry a location", "41/1,0/1,0/1", ExifInterface(source.absolutePath).getAttribute(ExifInterface.TAG_GPS_LATITUDE))

        val exif = ExifInterface(stored(store.importFrom(Uri.fromFile(source))).absolutePath)

        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
    }

    @Test
    fun `a rotated photo is stored upright`() = runTest {
        val source = jpeg(400, 200) {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
        }

        assertEquals(200 to 400, size(stored(store.importFrom(Uri.fromFile(source)))))
    }

    @Test
    fun `a large photo is scaled to 2048`() = runTest {
        assertEquals(2048 to 1536, size(stored(store.importFrom(Uri.fromFile(jpeg(4096, 3072))))))
    }

    @Test
    fun `a large odd-sized photo is scaled to 2048 on its long edge`() = runTest {
        assertEquals(1536 to 2048, size(stored(store.importFrom(Uri.fromFile(jpeg(3000, 4000))))))
    }

    @Test
    fun `a small photo is not scaled up`() = runTest {
        assertEquals(800 to 600, size(stored(store.importFrom(Uri.fromFile(jpeg(800, 600))))))
    }

    @Test
    fun `an unreadable file fails cleanly`() = runTest {
        val junk = File.createTempFile("junk", ".jpg", context.cacheDir).apply { writeBytes(ByteArray(512) { it.toByte() }) }

        assertEquals(PhotoImport.Failed(PhotoFailure.UNREADABLE), store.importFrom(Uri.fromFile(junk)))
        assertEquals(emptyList<String>(), photos.list().orEmpty().toList())
    }

    @Test
    fun `a capture is imported and its cache file removed`() = runTest {
        val capture = jpeg(400, 300)

        stored(store.importCapture(capture.absolutePath))

        assertFalse(capture.exists())
    }

    @Test
    fun `the sweep keeps referenced files only`() = runTest {
        val keep = (store.importFrom(Uri.fromFile(jpeg(100, 100))) as PhotoImport.Stored).fileName
        val orphan = (store.importFrom(Uri.fromFile(jpeg(100, 100))) as PhotoImport.Stored).fileName

        store.sweep(setOf(keep))

        assertTrue(store.fileFor(keep).exists())
        assertFalse(store.fileFor(orphan).exists())
    }

    @Test
    fun `the sweep clears stale captures`() = runTest {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val stale = File(cameraDir, "old.jpg").apply {
            writeText("x")
            setLastModified(System.currentTimeMillis() - 25 * 60 * 60 * 1000L)
        }
        val fresh = File(cameraDir, "new.jpg").apply {
            writeText("x")
            setLastModified(System.currentTimeMillis() - 60 * 60 * 1000L)
        }

        store.sweep(emptySet())

        assertFalse(stale.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `deleteAll empties the folder`() = runTest {
        store.importFrom(Uri.fromFile(jpeg(100, 100)))

        store.deleteAll()

        assertEquals(emptyList<String>(), photos.list().orEmpty().toList())
    }

    @Test
    fun `delete removes the named files`() = runTest {
        val name = (store.importFrom(Uri.fromFile(jpeg(100, 100))) as PhotoImport.Stored).fileName

        store.delete(listOf(name))

        assertFalse(store.fileFor(name).exists())
    }
}
