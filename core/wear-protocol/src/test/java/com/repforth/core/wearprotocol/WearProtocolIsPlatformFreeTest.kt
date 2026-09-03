package com.repforth.core.wearprotocol

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared protocol stays shared.
 *
 * Two apps compile against this module — the phone and, from 5.2, the watch —
 * and they have different platform surfaces. A `Context` or a `SystemClock` in
 * here would not fail to compile; it would quietly make the wire format
 * something only one side could construct, and the symptom would arrive much
 * later as a watch module that cannot be built.
 *
 * This is cheap to hold now and expensive to reintroduce, which is exactly when
 * a guard is worth writing. It is deliberately narrow: `android.*` is banned,
 * `kotlin.*`, `kotlinx.serialization.*` and `java.*` are not, because the point
 * is portability across the two Android targets rather than purity for its own
 * sake.
 *
 * Watched failing: adding `import android.os.SystemClock` to `WearProtocol.kt`.
 */
class WearProtocolIsPlatformFreeTest {

    /** Unit tests run with the module directory as the working dir. */
    private val sources = File("src/main")

    @Test
    fun `no source file imports an android type`() {
        val offenders = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> ANDROID_IMPORT.containsMatchIn(line) }
                    .map { (index, line) ->
                        "${file.name}:${index + 1}: ${line.trim()}"
                    }
            }
            .sorted()
            .toList()

        assertEquals(
            "The wear protocol is compiled by both the phone and the watch, so it " +
                "must not reach for a platform type:\n" + offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }

    /** A sanity check on the check: it must be reading files at all. */
    @Test
    fun `the guard is looking at the protocol sources`() {
        val found = sources.walkTopDown().count { it.isFile && it.extension == "kt" }
        assertEquals(
            "Expected to find the protocol sources under ${sources.absolutePath}.",
            EXPECTED_SOURCES,
            found,
        )
    }

    private companion object {
        val ANDROID_IMPORT = Regex("""^\s*import\s+android[x]?\.""")

        /** `WearProtocol.kt` and `WearAdmission.kt`. Update when a file is added. */
        const val EXPECTED_SOURCES = 2
    }
}
