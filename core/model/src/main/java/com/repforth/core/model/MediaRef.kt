package com.repforth.core.model

/**
 * A reference to a remote media file, resolved from `media-manifest.json` (§9).
 *
 * The hash and byte size travel with the URL deliberately: media is fetched
 * from an immutable pinned commit, so a download whose bytes do not match is a
 * corruption or a substitution, and both should be rejected rather than cached.
 *
 * [url] is null in `placeholder` builds, where licensed media is not shipped
 * (§6). Callers must handle that — it is the default flavour, not an edge case.
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
