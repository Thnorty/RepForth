package com.repforth.core.body

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photos in `filesDir/body-photos/`, re-encoded on the way in.
 *
 * Re-encoding is the privacy step, not only the size one: a JPEG written by
 * `Bitmap.compress` carries no EXIF at all, so the location and the camera model
 * a phone stamps on every photo are gone. Orientation is applied to the pixels
 * first, since the tag that described it goes with the rest.
 */
internal class FilePhotoStore(
    private val context: Context,
    private val io: CoroutineDispatcher,
) : PhotoStore {

    /** What Hilt builds. The dispatcher is a seam for tests, as in `DefaultDataTransfer`. */
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Dispatchers.IO)

    private val folder: File get() = File(context.filesDir, PHOTOS).apply { mkdirs() }
    private val cameraFolder: File get() = File(context.cacheDir, CAMERA).apply { mkdirs() }

    override suspend fun importFrom(source: Uri): PhotoImport = withContext(io) {
        import { context.contentResolver.openInputStream(source) ?: throw IOException("no stream for $source") }
    }

    override fun newCapture(): PhotoCapture {
        val file = File(cameraFolder, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        return PhotoCapture(uri, file.absolutePath)
    }

    override suspend fun importCapture(path: String): PhotoImport = withContext(io) {
        val capture = File(path)
        try {
            import { capture.inputStream() }
        } finally {
            capture.delete()
        }
    }

    override fun fileFor(fileName: String): File = File(folder, fileName)

    override suspend fun delete(fileNames: Collection<String>) = withContext(io) {
        fileNames.forEach { File(folder, it).delete() }
    }

    override suspend fun sweep(referenced: Set<String>) = withContext(io) {
        folder.listFiles().orEmpty().filterNot { it.name in referenced }.forEach { it.delete() }
        val staleBefore = System.currentTimeMillis() - STALE_CAPTURE_MS
        cameraFolder.listFiles().orEmpty().filter { it.lastModified() < staleBefore }.forEach { it.delete() }
    }

    override suspend fun deleteAll() = withContext(io) {
        folder.listFiles().orEmpty().forEach { it.delete() }
        cameraFolder.listFiles().orEmpty().forEach { it.delete() }
        Unit
    }

    /** Decode, turn upright, scale down, write — or fail leaving nothing behind. */
    private fun import(open: () -> InputStream): PhotoImport {
        val bitmap = try {
            decode(open)
        } catch (e: IOException) {
            null
        } ?: return PhotoImport.Failed(PhotoFailure.UNREADABLE)

        val upright = scaled(rotated(bitmap, orientation(open)))
        val name = "${UUID.randomUUID()}.jpg"
        val temporary = File(folder, ".$name.tmp")
        return try {
            temporary.outputStream().use { out ->
                if (!upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) throw IOException("compress failed")
            }
            if (!temporary.renameTo(File(folder, name))) throw IOException("rename failed")
            PhotoImport.Stored(name)
        } catch (e: IOException) {
            temporary.delete()
            PhotoImport.Failed(PhotoFailure.WRITE_FAILED)
        }
    }

    /** Decoded at the smallest power-of-two reduction that keeps the long edge at [MAX_EDGE] or more. */
    private fun decode(open: () -> InputStream): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return open().use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun orientation(open: () -> InputStream): Int = try {
        open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (e: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun rotated(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Down to [MAX_EDGE] on the long edge, never up. */
    private fun scaled(bitmap: Bitmap): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= MAX_EDGE) return bitmap
        val factor = MAX_EDGE.toDouble() / longEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).roundToInt(),
            (bitmap.height * factor).roundToInt(),
            true,
        )
    }

    private companion object {
        const val PHOTOS = "body-photos"
        const val CAMERA = "camera"
        const val AUTHORITY_SUFFIX = ".bodyphotos"
        const val MAX_EDGE = 2048
        const val QUALITY = 90
        const val STALE_CAPTURE_MS = 24 * 60 * 60 * 1000L
    }
}
