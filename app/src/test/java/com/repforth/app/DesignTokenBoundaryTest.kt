package com.repforth.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two rules that hold the design system together now that it spans two modules.
 *
 * `core:designtokens` was split out of `core:designsystem` so the watch could
 * read the palette, the faces and the numeric scale without dragging phone
 * Material 3 onto a Wear classpath. The two halves deliberately **share the
 * package** `com.repforth.core.designsystem.theme`, because this is one design
 * system that two Material implementations read rather than two design systems.
 *
 * That decision buys a diff with no import churn and costs two things the
 * compiler used to do for free. Both are re-created here.
 */
class DesignTokenBoundaryTest {

    /** Repo root. Unit tests run with the module directory as the working dir. */
    private val root = File("..")

    private val tokens = File(root, "core/designtokens/src/main/java/com/repforth/core/designsystem/theme")
    private val system = File(root, "core/designsystem/src/main/java/com/repforth/core/designsystem/theme")

    /**
     * **A file name may not appear in both halves of the shared package.**
     *
     * Kotlin compiles top-level declarations into a facade class named after the
     * file, so `Type.kt` in either module becomes
     * `com.repforth.core.designsystem.theme.TypeKt`. Two of those on one
     * classpath is a collision, and the resolution is silent: one shadows the
     * other and every symbol in the loser reads as unresolved, from a module
     * that plainly declares a dependency on it.
     *
     * That is exactly how the split was found to be wrong the first time. The
     * tokens compiled, the design system compiled, and `feature:home` failed on
     * `Unresolved reference 'RepForthNumeric'` — a symbol sitting in a file it
     * had a perfectly good `api` path to. The fix was renaming the two survivors
     * in `core:designsystem` to `PhoneTypography.kt` and `PhoneShapes.kt`, which
     * is also what they now contain.
     *
     * Adding `Color.kt` to `core:designsystem` tomorrow would reproduce it, and
     * nothing but this test would say so.
     */
    @Test
    fun `the two halves of the shared package share no file name`() {
        val inTokens = kotlinFiles(tokens)
        val inSystem = kotlinFiles(system)

        assertTrue("Found no sources in core:designtokens, so this is reading the wrong path", inTokens.isNotEmpty())
        assertTrue("Found no sources in core:designsystem, so this is reading the wrong path", inSystem.isNotEmpty())

        assertEquals(
            "These file names exist in both halves of com.repforth.core.designsystem.theme. " +
                "Kotlin turns each into the same facade class, and one will silently shadow " +
                "the other. Rename one side.",
            emptySet<String>(),
            inTokens intersect inSystem,
        )
    }

    /**
     * **Product code reaches semantic roles, never a raw tone.**
     *
     * `Tone` was `internal` and the compiler enforced this. Kotlin scopes
     * `internal` to a module, and the watch needs the raw tones to assemble a
     * Wear `ColorScheme`, so it had to become public when it moved. This is what
     * replaces the compiler.
     *
     * The files allowed are the ones that legitimately turn tones into roles:
     * the palette itself, the phone's theme, the watch's, and the progress ring,
     * whose track is a tone Material has no slot for. A screen
     * that wants "the accent" wants `colorScheme.primary`, and a screen that
     * wants a colour Material has no slot for wants `LocalRepForthColors`.
     */
    @Test
    fun `nothing outside the theme assemblies names a raw tone`() {
        val allowed = setOf("Color.kt", "Theme.kt", "WearTheme.kt", "ProgressRing.kt")

        val offenders = sequenceOf("core", "feature", "app", "wear")
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("${File.separator}build${File.separator}") }
            // Product code only. `LaunchBackgroundTest` asserts that the XML
            // launch colour still equals `Tone.N6`, which is the one place a
            // raw tone is the subject rather than a shortcut -- and this file
            // names the pattern in its own regex.
            .filter { it.path.contains("src${File.separator}main") }
            .filterNot { it.name in allowed }
            .filter { TONE_USE.containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()

        assertEquals(
            "Reach a colour through MaterialTheme.colorScheme or LocalRepForthColors, " +
                "not through Tone. The palette is layered so a screen cannot hard-code a swatch.",
            emptyList<String>(),
            offenders,
        )
    }

    private fun kotlinFiles(dir: File): Set<String> =
        dir.listFiles().orEmpty().filter { it.isFile && it.extension == "kt" }.map { it.name }.toSet()

    private companion object {
        /**
         * `Tone.Lime80` and friends, and nothing else.
         *
         * Anchored on a word boundary so `RingTone.Rest` does not match — it did
         * in the first grep that went looking for this, which is worth knowing
         * before trusting a count from a plainer pattern.
         */
        val TONE_USE = Regex("""\bTone\.[A-Z]""")
    }
}
