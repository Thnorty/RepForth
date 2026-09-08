package com.repforth.core.media

import com.repforth.core.media.manifest.MediaManifest
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every build fetches the upstream exercise media, and this is what says so.
 *
 * **Read this before "fixing" a failure here.** The claim it holds is not a code
 * detail — it is what `PRIVACY.md` tells users about network activity, what
 * `NOTICE.md` and `README.md` tell anyone building this about imagery they have
 * no licence to, and what §6 of the guideline records as a decision. If this
 * test goes red, one of those documents has become wrong, and correcting the
 * document is part of the change rather than a follow-up.
 *
 * It exists because the opposite claim survived for months in all four of those
 * places at once. Every one of them said the `placeholder` flavour shipped no
 * imagery and made no media requests. Nothing could fail when that stopped being
 * true, because nothing had ever been true: `PlaceholderMediaResolver` was bound
 * nowhere, the manifest shipped in `main`, and no source read the flavour at run
 * time. Four documents agreeing with each other is not evidence, and this is the
 * cheapest thing that is.
 */
class MediaIsInEveryBuildTest {

    /** Unit tests run with the module directory as the working dir. */
    private val manifest = File("src/main/assets/media-manifest.json")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * `main`, not a flavour source set — which is the whole mechanism.
     *
     * A file here reaches `placeholder` and `licensed` alike. Moving it under
     * `src/licensed/assets` is precisely the change that would make the retired
     * claim true again, and it would otherwise be invisible: nothing would fail
     * to compile, and the default build would simply stop showing pictures.
     */
    @Test
    fun `the media manifest ships in the source set every flavour gets`() {
        assertTrue(
            "Expected the manifest at ${manifest.absolutePath}. If it moved to a " +
                "flavour source set, PRIVACY.md, NOTICE.md and README.md are now wrong.",
            manifest.isFile,
        )
    }

    /** No flavour source set may shadow it, for the same reason. */
    @Test
    fun `this module has no flavour source sets`() {
        val flavoured = File("src").listFiles()
            .orEmpty()
            .filter { it.isDirectory && (it.name.startsWith("placeholder") || it.name.startsWith("licensed")) }
            .map { it.name }
            .sorted()

        assertEquals(
            "A flavour source set here could override the manifest or the resolver " +
                "binding, which is the shape of the claim these tests retired:\n" +
                flavoured.joinToString("\n"),
            emptyList<String>(),
            flavoured,
        )
    }

    /**
     * The manifest points at a real host, with real entries.
     *
     * A manifest that parsed but carried nothing would satisfy the file check
     * above while the app fetched nothing — which is the retired behaviour,
     * reached by a different route.
     */
    @Test
    fun `the manifest resolves real upstream media`() {
        val parsed = json.decodeFromString<MediaManifest>(manifest.readText())

        assertTrue(
            "The base URL is what a build actually contacts: ${parsed.baseUrl}",
            parsed.baseUrl.startsWith("https://"),
        )
        assertTrue(
            "The catalog has 1324 exercises; a manifest with ${parsed.entries.size} " +
                "entries is not the shipped one",
            parsed.entries.size > MIN_ENTRIES,
        )
        assertTrue(
            "Attribution rides with the manifest (§6) and is shown wherever media is",
            parsed.attribution.contains("Gym visual"),
        )
    }

    /**
     * One resolver, so a flavour cannot quietly acquire a second.
     *
     * The retired claim was embodied by a second implementation that answered
     * "no media" to everything and was bound nowhere. Reintroducing one is the
     * likeliest way for the documents to drift back out of step, and it is the
     * kind of change that reads as harmless in review.
     */
    @Test
    fun `there is exactly one MediaResolver implementation`() {
        val implementations = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { IMPLEMENTS.containsMatchIn(it) }
                    .map { "${file.name}: ${it.trim()}" }
            }
            .sorted()
            .toList()

        assertEquals(
            "Every build resolves media the same way, through the manifest. A " +
                "second implementation means that is no longer true, and " +
                "PRIVACY.md, NOTICE.md and README.md describe the old world:\n" +
                implementations.joinToString("\n"),
            1,
            implementations.size,
        )
    }

    private companion object {
        /**
         * Implementing the interface and opening a body.
         *
         * Deliberately not anchored to a closing paren. The first version was
         * `\) : MediaResolver \{`, which requires a constructor — and a
         * resolver declared `class X : MediaResolver {` slipped straight past
         * it. Found by adding exactly that and watching the guard stay green,
         * which is the only reason it is written this way now.
         *
         * `@Binds` in `MediaModule` returns `): MediaResolver` with no body, so
         * it does not match.
         */
        val IMPLEMENTS = Regex(""":\s*MediaResolver\s*\{""")

        const val MIN_ENTRIES = 1_000
    }
}
