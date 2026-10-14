package com.repforth.core.body

import android.net.Uri
import java.io.File

enum class PhotoFailure {
    /** Not an image this phone can decode: corrupt, or an unsupported format. */
    UNREADABLE,

    /** Decoded, but could not be written — almost always a full disk. */
    WRITE_FAILED,
}

sealed interface PhotoImport {
    data class Stored(val fileName: String) : PhotoImport
    data class Failed(val reason: PhotoFailure) : PhotoImport
}

/** Where the camera app should write, and the path to import from afterwards. */
data class PhotoCapture(val uri: Uri, val path: String)

/**
 * The progress photo files, in app-private storage.
 *
 * Every photo is re-encoded on the way in, upright, scaled down and stripped of
 * its metadata, so nothing about where it was taken comes along. The files never
 * leave the device: they are not exported, and the app disables Android backup.
 */
interface PhotoStore {

    suspend fun importFrom(source: Uri): PhotoImport

    /** A file for the camera app to write into, reachable through the app's FileProvider. */
    fun newCapture(): PhotoCapture

    /** Imports what the camera wrote at [path], then deletes that file. */
    suspend fun importCapture(path: String): PhotoImport

    fun fileFor(fileName: String): File

    suspend fun delete(fileNames: Collection<String>)

    /** Removes stored photos that [referenced] does not name, and stale captures. */
    suspend fun sweep(referenced: Set<String>)

    suspend fun deleteAll()
}
