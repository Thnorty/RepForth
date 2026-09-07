package com.repforth.core.wearprotocol

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A path nothing listens on delivers nothing, and says so nowhere.
 *
 * The Data Layer has no error for "sent to a path with no receiver". The phone
 * publishes, the call succeeds, and the watch never hears — which is the same
 * observable behaviour as a watch that is out of range, and is why this is the
 * kind of mistake that costs a hardware session rather than a compile.
 *
 * Both apps declare a `pathPrefix` in their manifest, and an intent filter
 * cannot read a Kotlin constant. That is the forced duplicate this guards: the
 * prefix in two XML files against the paths in [WearPaths].
 *
 * The files are declared as inputs of the test task in `GuardTestInputs.kt`.
 * They are read through `java.io.File` at runtime, so without that Gradle
 * reports UP-TO-DATE on exactly the edit this exists to catch.
 */
class WearPathsTest {

    /** Unit tests run with the module directory as the working dir. */
    private val root = File("../..")

    @Test
    fun `every path sits under the prefix the manifests filter on`() {
        val outside = WearPaths.all.filterNot { it.startsWith(WearPaths.PREFIX + "/") }

        assertEquals(
            "A path outside the filtered prefix is delivered to nobody, and the " +
                "send still succeeds:\n" + outside.joinToString("\n"),
            emptyList<String>(),
            outside,
        )
    }

    @Test
    fun `the watch listens on the prefix`() {
        assertPrefixIsDeclaredIn(WATCH_MANIFEST)
    }

    @Test
    fun `the phone listens on the prefix`() {
        assertPrefixIsDeclaredIn(PHONE_MANIFEST)
    }

    /**
     * The paths are distinct.
     *
     * Two of them being equal would route a command into the snapshot handler,
     * or an alert into the command handler, and each side's exact-path check
     * would happily agree. Cheap to state, and impossible to see by reading
     * three lines that differ by one word.
     */
    @Test
    fun `no two paths are the same`() {
        assertEquals(WearPaths.all.size, WearPaths.all.toSet().size)
    }

    private fun assertPrefixIsDeclaredIn(path: String) {
        val manifest = File(root, path)
        // A sanity check on the check: a walk that finds nothing would report a
        // missing filter, which looks exactly like the defect and is not one.
        assertTrue(
            "Expected a manifest at ${manifest.absolutePath}",
            manifest.isFile,
        )

        val text = manifest.readText()
        assertTrue(
            "$path must filter on android:pathPrefix=\"${WearPaths.PREFIX}\", or " +
                "nothing sent to any of ${WearPaths.all} arrives:\n" + text,
            """android:pathPrefix="${WearPaths.PREFIX}"""" in text,
        )
    }

    private companion object {
        const val WATCH_MANIFEST = "wear/src/main/AndroidManifest.xml"
        const val PHONE_MANIFEST = "feature/session/src/main/AndroidManifest.xml"
    }
}
