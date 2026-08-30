package com.repforth.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where this app is allowed to touch the network, and from how many places.
 *
 * This replaces `CleartextGuardTest`, and the reason is worth stating plainly
 * rather than leaving as a rename. That test asserted three things which
 * together made it safe for the manifest to permit cleartext: one HTTP client,
 * one caller, and an `EndpointPolicy` check before every request. **The third
 * no longer exists.** The maintainer decided the app should not inspect what
 * the user types — if the server answers, that is the answer — and §8 was
 * amended to match.
 *
 * So the security claim this file can make is smaller, and it is stated
 * honestly: **nothing prevents an API key being sent over `http://`.** What is
 * still worth holding is the shape of the boundary — one module reaches the
 * network, through one file — because that is what makes the question "where
 * does this app talk out, and with what" answerable at all, and because a
 * second HTTP client appearing quietly is how a local-first app stops being one.
 *
 * Watched failing: adding okhttp to `feature/settings`, and adding a `newCall(`
 * outside `ProviderHttp.kt`.
 */
class NetworkBoundaryTest {

    /** Repo root. Unit tests run with the module directory as the working dir. */
    private val root = File("..")

    /**
     * Exactly one module may declare an HTTP client.
     *
     * Not a cleartext control any more — a scope control. Every outbound
     * request belongs to the AI provider feature, which is optional and which
     * the user switches on by pasting a key. A client anywhere else would mean
     * the app talks to the network for some other reason, and §4's promise that
     * everything stays on the phone would need rewording rather than defending.
     */
    /**
     * Exactly two modules may declare an HTTP client: core:ai and core:media (§9, §18).
     *
     * Every outbound request belongs to either the AI provider feature (optional BYOK)
     * or on-demand exercise media downloading. A client anywhere else would mean
     * the app talks to the network for some other reason, breaking the local-first
     * architecture boundary.
     */
    @Test
    fun `only core ai and core media depend on an http client`() {
        val declaring = root.walkTopDown()
            .onEnter { it.name !in IGNORED_DIRS }
            .filter { it.name == "build.gradle.kts" }
            .filter { "okhttp" in it.readText() }
            .map { it.parentFile!!.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()

        assertEquals(
            "An HTTP client is declared outside core:ai and core:media, in $declaring. This app " +
                "makes network requests for exactly two features; a " +
                "third client means that is no longer true.",
            listOf("core/ai", "core/media"),
            declaring,
        )
    }

    /**
     * Exactly two files may turn a request into a call.
     *
     * ProviderHttp for AI requests and MediaDownloader for exercise media downloads.
     */
    @Test
    fun `only ProviderHttp and MediaDownloader turn a request into a call`() {
        val callers = root.walkTopDown()
            .onEnter { it.name !in IGNORED_DIRS }
            .filter {
                it.isFile && it.extension == "kt" &&
                    "/src/test/" !in it.path.replace('\\', '/')
            }
            .filter { "newCall(" in it.readText() }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()

        assertEquals(
            "Something other than ProviderHttp.kt and MediaDownloader.kt is making HTTP calls: $callers.",
            listOf(
                "core/ai/src/main/java/com/repforth/core/ai/http/ProviderHttp.kt",
                "core/media/src/main/java/com/repforth/core/media/download/MediaDownloader.kt",
            ),
            callers,
        )
    }

    /**
     * The manifest permits cleartext, and now nothing narrows it.
     *
     * Asserted so that the state of affairs is written down in a place that
     * fails if someone quietly reverses it — in either direction. Turning it off
     * would break a local model server on the user's own network, which is the
     * case §8 names first; and there is no longer an `EndpointPolicy` to soften
     * the landing.
     */
    @Test
    fun `cleartext is permitted, deliberately and without a second check`() {
        val config = File("src/main/res/xml/network_security_config.xml").readText()

        assertTrue(
            "The network security config no longer permits cleartext. Nothing " +
                "else in the app checks the address, so this is the only place " +
                "the decision is recorded — change docs/PLAN.md and §8 with it.",
            """cleartextTrafficPermitted="true"""".toRegex().containsMatchIn(config),
        )
    }

    private companion object {
        val IGNORED_DIRS = setOf("build", ".git", ".gradle", "design-system", "dataset")
    }
}
