package com.repforth.app

import com.repforth.core.model.UserPreferences
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every setting the app offers has to change something.
 *
 * Three shipped that did not. "Vibration — on completing and skipping a set"
 * was on by default, and the repository contained no `performHapticFeedback`,
 * no `Vibrator` and no `VibrationEffect` at all, on the phone or the watch.
 * "Keep screen on" was on by default, and [UserPreferences] argued in a comment
 * that the screen going dark mid-set is a worse failure than the battery cost —
 * while nothing anywhere asked for the flag. `onboardingComplete` was neither
 * written nor read by anything, the app having settled on the profile's
 * existence as the answer to that question, and has been deleted.
 *
 * A fourth was fixed before this test existed and is why the shape is familiar:
 * the reduced-motion switch only swapped an animated GIF for a thumbnail, while
 * every real animation in the app ignored it.
 *
 * **None of them could fail.** The preference was stored, read back, bound to a
 * switch that moved, and survived a process restart — so the round-trip test
 * passed, the screenshot showed a switch, and the accessibility check found it
 * announced and large enough to tap. What none of those ask is whether anything
 * downstream reads the value, and reading it is the entire point of a setting.
 *
 * `feature:settings` is excluded because writing a preference and drawing its
 * switch is what that module is for — a preference read only there is precisely
 * the defect. `core:model` and `core:datastore` are where they are declared and
 * persisted.
 */
class PreferenceReachTest {

    /** Repo root. Unit tests run with the module directory as the working dir. */
    private val root = File("..")

    @Test
    fun `every preference is read by something that is not settings`() {
        val fields = preferenceFields()
        assertTrue(
            "UserPreferences parsed to no fields, so this is reading the wrong file",
            fields.isNotEmpty(),
        )

        val sources = kotlinSources()
        // Counted, because a walk that finds nothing reports every preference as
        // unread, which looks exactly like the defect and is not one.
        // `WearProtocolIsPlatformFreeTest` guards itself the same way.
        assertTrue(
            "The walk found ${sources.size} main sources, so it is looking in the " +
                "wrong place: root=${root.absolutePath}",
            sources.size > MIN_SOURCES,
        )

        val consumers = sources
            .filterNot { file -> EXCLUDED.any { file.invariantPath().startsWith(it) } }
            .map { it.readText() }

        val unread = fields
            .filterNot { field -> consumers.any { wordRegex(field).containsMatchIn(it) } }
            .sorted()

        assertEquals(
            "These preferences are stored, displayed, and acted on by nothing — so " +
                "the control for each one does nothing:\n" + unread.joinToString("\n"),
            emptyList<String>(),
            unread,
        )
    }

    /**
     * The preference being *read off something*, not merely named.
     *
     * The leading dot is what makes this mean anything. Without it the check
     * was satisfied by any parameter that happened to share the name — and one
     * did: `RepForthTheme(hapticsEnabled = ...)` passes the value along without
     * being the thing that acts on it, so removing the only real read left the
     * test green. Requiring `.hapticsEnabled` asks for a property access, which
     * is what reading a preference looks like.
     *
     * Assembled with [Regex.escape] rather than written as a pattern: in an
     * ordinary Kotlin string the escape for a word boundary is the backspace
     * character instead, so a regex spelled that way matches nothing and
     * reports every preference as unread — indistinguishable from the defect.
     */
    private fun wordRegex(name: String) =
        Regex("\\." + Regex.escape(name) + "(?![A-Za-z0-9_])")

    /** The property names of [UserPreferences], read from the file that declares them. */
    private fun preferenceFields(): List<String> =
        File(root, MODEL).readText()
            .substringAfter("data class UserPreferences(")
            .substringBefore("\n)")
            .let { body -> PROPERTY.findAll(body).map { it.groupValues[1] }.toList() }

    private fun kotlinSources(): List<File> =
        root.walkTopDown()
            .onEnter { it.name !in IGNORED_DIRS }
            .filter { it.isFile && it.extension == "kt" }
            .filter { "/src/main/" in it.invariantPath() }
            .toList()

    private fun File.invariantPath(): String = relativeTo(root).invariantSeparatorsPath

    private companion object {
        const val MODEL = "core/model/src/main/java/com/repforth/core/model/UserPreferences.kt"

        val IGNORED_DIRS = setOf("build", ".gradle", ".git", "design-system", ".idea")

        /** The repo had well over two hundred main sources when this was written. */
        const val MIN_SOURCES = 100

        val PROPERTY = Regex("""val (\w+):""")

        val EXCLUDED = listOf("core/model/", "core/datastore/", "feature/settings/")
    }
}
