package com.repforth.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Motion is spelled in tokens, and the reduced-motion setting can reach it.
 *
 * The setting existed before this test did and only ever swapped an animated
 * GIF for a thumbnail; every actual animation in the app ran regardless of it.
 * `RepForthTheme` now provides `LocalReducedMotion` and `rfTween` reads it, but
 * a spec built with a bare `tween(300)` still cannot be switched off — and it
 * looks completely normal in review, which is why it needs a guard rather than
 * a convention.
 *
 * Watched failing: putting `durationMillis = 300` into `CoachScreen`, and
 * adding a `rememberInfiniteTransition` to a file with no `LocalReducedMotion`
 * in it.
 */
class MotionTokenTest {

    /** Repo root. Unit tests run with the module directory as the working dir. */
    private val root = File("..")

    /**
     * Durations come from `Dur`, not from a number typed at the call site.
     *
     * `core:designsystem` is exempt because that is where the numbers are
     * defined — `Motion.kt` is the one file allowed to know that `short` is
     * 150ms, exactly as `Dimens.kt` is the only file allowed to know a touch
     * target is 48dp.
     */
    @Test
    fun `animation durations come from the motion tokens`() {
        val offenders = kotlinSources()
            .filterNot { it.invariantPath().startsWith("core/designsystem/") }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> LITERAL_DURATION.containsMatchIn(line) }
                    .map { (index, line) ->
                        "${file.invariantPath()}:${index + 1}: ${line.trim()}"
                    }
            }
            .sorted()

        assertEquals(
            "These pass a hard-coded duration instead of a Dur token, so the " +
                "reduced-motion setting cannot shorten them:\n" + offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * An endless animation has to consult the setting itself.
     *
     * It is the one spec a zero duration cannot express: `tween(0)` inside an
     * `infiniteRepeatable` repeats instantly and forever rather than not
     * animating. So the file has to branch on `LocalReducedMotion` and not
     * start the transition at all, which is what `CoachScreen` does.
     */
    @Test
    fun `an infinite animation reads the reduced motion setting`() {
        val offenders = kotlinSources()
            .filterNot { it.invariantPath().startsWith("core/designsystem/") }
            .filter { "rememberInfiniteTransition" in it.readText() }
            .filterNot { "LocalReducedMotion" in it.readText() }
            .map { it.invariantPath() }
            .sorted()

        assertEquals(
            "These start an endless animation without consulting " +
                "LocalReducedMotion, so it runs even when the user asked for " +
                "less movement:\n" + offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * A raw `tween` is either routed through `rfTween` or justified in place.
     *
     * The duration rule above is not enough on its own: `tween(Dur.medium)`
     * uses the token, passes it, and still cannot be switched off, because the
     * collapse happens in `rfTween` rather than in the number.
     *
     * The exemption is deliberate and is the `CoachScreen` case — an
     * `infiniteRepeatable` cannot take a zero duration, so a file that branches
     * on `LocalReducedMotion` itself has already answered the question this
     * guard asks.
     */
    @Test
    fun `a raw tween is routed through rfTween or justified`() {
        val offenders = kotlinSources()
            .filterNot { it.invariantPath().startsWith("core/designsystem/") }
            .filter { file ->
                val text = file.readText()
                RAW_TWEEN.containsMatchIn(text) &&
                    "LocalReducedMotion" !in text
            }
            .map { it.invariantPath() }
            .sorted()

        assertEquals(
            "These build an animation spec with a bare tween and never consult " +
                "the reduced-motion setting. Use rfTween, or read " +
                "LocalReducedMotion and say why a plain tween is right:\n" +
                offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }

    private fun kotlinSources(): List<File> =
        root.walkTopDown()
            .onEnter { it.name !in IGNORED_DIRS }
            .filter { it.isFile && it.extension == "kt" }
            .filter { "/src/main/" in it.invariantPath() }
            .toList()

    private fun File.invariantPath(): String = relativeTo(root).invariantSeparatorsPath

    private companion object {
        val IGNORED_DIRS = setOf("build", ".gradle", ".git", "design-system", ".idea")

        /**
         * `durationMillis = 300`, but not `durationMillis = Dur.medium`.
         *
         * Only the named parameter, on purpose: `tween(300)` positionally is
         * also a literal, but `delayMillis` and several unrelated APIs take a
         * bare Int first, and a guard that fires on the wrong thing gets
         * suppressed rather than fixed.
         */
        val LITERAL_DURATION = Regex("""durationMillis\s*=\s*\d""")

        /**
         * `tween(` but not `rfTween(`. The negative lookbehind is what stops
         * every correct call site being reported as a violation of itself.
         *
         * The optional type argument is not decoration: the first version of
         * this read `tween\s*\(` and was watched failing to catch
         * `tween<Float>(`, which is how the API is written whenever the target
         * type cannot be inferred. A guard with a hole that shape passes
         * exactly the call sites most likely to be wrong.
         */
        val RAW_TWEEN = Regex("""(?<![A-Za-z])tween\s*(?:<[^>]*>)?\s*\(""")
    }
}
