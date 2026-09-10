package com.repforth.wear

import android.util.Base64

/**
 * A picture for the tests, generated rather than borrowed.
 *
 * §6 forbids committing media bytes, and a golden is committed — so this is a
 * 16×16 two-frame GIF built for the purpose. Four flat quadrants, because a
 * single colour renders as a disc that proves nothing about whether an image was
 * drawn at all, and the two frames swap those quadrants so a picture that is
 * playing when it should not be is visible rather than merely wrong.
 *
 * It is a real animated GIF, which matters: `ImageDecoder` returns an
 * `AnimatedImageDrawable` only for one of those, and "does the watch animate
 * what the phone now sends" is not answerable with a still.
 */
internal fun animatedGif(): ByteArray = Base64.decode(ANIMATED_GIF, Base64.DEFAULT)

private const val ANIMATED_GIF =
    "R0lGODlhEAAQAIEAAPLy8pqamuhqMy4uLiH/C05FVFNDQVBFMi4wAwEAAAAh+QQAFAAAACwAAAAA" +
        "EAAQAAAIRgAFCBwoMIDBgwYJEkSIUOFAhgcdFoQYQKIAihUlYrS4USPFASBDggRAsiRJkSJNmkQZ" +
        "UmVJliNdAoA5QOZMmDZp5sQpMyAAIfkEARQABAAsAAAAABAAEACB8vLympqa6GozLi4uCEYAAwgc" +
        "KFCAwYMGCRJEiFDhQIYHHRaEKEBiAIoVJWK0uFEjRQAgQ4IcQLIkSZEiTZpEGVJlSZYjXQ6ACUDm" +
        "TJg2aebEKTMgADs="
