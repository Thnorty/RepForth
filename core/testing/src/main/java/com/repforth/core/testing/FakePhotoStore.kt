package com.repforth.core.testing

import android.net.Uri
import com.repforth.core.body.PhotoCapture
import com.repforth.core.body.PhotoFailure
import com.repforth.core.body.PhotoImport
import com.repforth.core.body.PhotoStore
import java.io.File

/**
 * Photo files without files: imports hand out names, and every request is
 * recorded. Set [nextImport] to make the next import fail.
 */
class FakePhotoStore(private val root: File = File("build/fake-photos")) : PhotoStore {

    var nextImport: PhotoImport? = null
    val imported = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var sweeps = 0
        private set
    var captures = 0
        private set

    private var counter = 0

    private fun store(): PhotoImport {
        nextImport?.let {
            nextImport = null
            return it
        }
        val name = "photo-${counter++}.jpg"
        imported += name
        return PhotoImport.Stored(name)
    }

    override suspend fun importFrom(source: Uri): PhotoImport = store()

    override fun newCapture(): PhotoCapture {
        captures++
        val path = File(root, "capture-$captures.jpg").path
        return PhotoCapture(Uri.parse("content://fake/$captures"), path)
    }

    override suspend fun importCapture(path: String): PhotoImport = store()

    override fun fileFor(fileName: String): File = File(root, fileName)

    override suspend fun delete(fileNames: Collection<String>) {
        deleted += fileNames
    }

    override suspend fun sweep(referenced: Set<String>) {
        sweeps++
    }

    override suspend fun deleteAll() {
        deleted += imported
    }

    companion object {
        val UNREADABLE = PhotoImport.Failed(PhotoFailure.UNREADABLE)
    }
}
