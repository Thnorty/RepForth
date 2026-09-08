package com.repforth.core.model

/**
 * A reference to a remote media file, resolved from `media-manifest.json` (§9).
 *
 * The hash and byte size travel with the URL deliberately: media is fetched
 * from an immutable pinned commit, so a download whose bytes do not match is a
 * corruption or a substitution, and both should be rejected rather than cached.
 *
 * [url] is null when the manifest has no entry for the exercise. Callers must
 * handle that, and the UI does: it draws an icon rather than art.
 *
 * It is **not** null "in placeholder builds". That is what this comment used to
 * say, and it was wrong in a way that mattered: the manifest ships in `main`, so
 * every flavour resolves real upstream URLs and downloads from them (§6).
 */
data class MediaRef(
    val url: String?,
    val sha256: String?,
    val byteSize: Long?,
) {
    val isAvailable: Boolean get() = url != null

    companion object {
        /** Used by `placeholder` builds, where the UI draws its own stand-in. */
        val Unavailable = MediaRef(url = null, sha256 = null, byteSize = null)
    }
}
