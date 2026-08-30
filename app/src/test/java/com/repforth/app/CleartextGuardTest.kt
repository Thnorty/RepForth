package com.repforth.app

import com.repforth.core.model.EndpointPolicy
import com.repforth.core.model.EndpointVerdict
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app is the only thing standing between an API key and a cleartext socket.
 *
 * `network_security_config.xml` permits cleartext, because it has to: it lists
 * hosts, and "any address on the user's own network" is not a host. The rule
 * that actually narrows it — private numeric addresses only, and only when the
 * user has switched it on — lives in [EndpointPolicy].
 *
 * That is a fair trade only while three things stay true, and each one is a
 * single careless edit away from not being:
 *
 * 1. One module depends on an HTTP client.
 * 2. One file in it makes calls.
 * 3. That file consults [EndpointPolicy] first.
 *
 * If any of them breaks, the manifest is the app's whole cleartext policy, and
 * the manifest says yes to everything. So they are asserted rather than
 * remembered.
 *
 * Each is watched failing: adding okhttp to `feature/settings`, adding a
 * `newCall(` outside `ProviderHttp.kt`, and removing the policy check from it.
 */
class CleartextGuardTest {

    /** Repo root. Unit tests run with the module directory as the working dir. */
    private val root = File("..")

    @Test
    fun `the platform permits cleartext, which is why the rest of this file exists`() {
        val config = File("src/main/res/xml/network_security_config.xml").readText()

        assertTrue(
            "The network security config no longer permits cleartext. If that " +
                "is deliberate, a local model server on the user's own network " +
                "stops working and §8's developer setting becomes a switch that " +
                "does nothing — say so in docs/PLAN.md rather than leaving this " +
                "test asserting the opposite of the code.",
            """cleartextTrafficPermitted="true"""".toRegex().containsMatchIn(config),
        )
    }

    /**
     * Exactly one module may declare an HTTP client.
     *
     * A second one would be a second place cleartext can leave the app, and it
     * would not go through [EndpointPolicy] unless somebody thought to make it.
     */
    @Test
    fun `only core ai depends on an http client`() {
        val declaring = root.walkTopDown()
            .onEnter { it.name !in IGNORED_DIRS }
            .filter { it.name == "build.gradle.kts" }
            .filter { "okhttp" in it.readText() }
            // Repo-relative, so the message names the module rather than
            // printing ".." for the root build file.
            .map { it.parentFile.relativeTo(root).invariantSeparatorsPath }
            .toList()

        assertEquals(
            "An HTTP client is declared outside core:ai, in $declaring. Every " +
                "outbound request has to go through ProviderHttp, which is the " +
                "only thing that checks the address before sending.",
            listOf("core/ai"),
            declaring,
        )
    }

    /**
     * Exactly one file may turn a request into a call.
     *
     * `newCall` is the last step before the socket. Anywhere else, and the
     * address was never checked.
     */
    @Test
    fun `only ProviderHttp turns a request into a call`() {
        val callers = root.walkTopDown()
            .onEnter { it.name !in IGNORED_DIRS }
            .filter { it.isFile && it.extension == "kt" && "/src/test/" !in it.path.replace('\\', '/') }
            .filter { "newCall(" in it.readText() }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toList()

        assertEquals(
            "Something other than ProviderHttp.kt is making HTTP calls: " +
                "$callers. That code has not checked the endpoint, so it can " +
                "send an API key in clear text to any address the user typed.",
            listOf("core/ai/src/main/java/com/repforth/core/ai/http/ProviderHttp.kt"),
            callers,
        )
    }

    @Test
    fun `the caller checks the endpoint before it sends`() {
        val source = File(root, "core/ai/src/main/java/com/repforth/core/ai/http/ProviderHttp.kt")
        assertTrue("Expected ${source.absolutePath} to exist", source.exists())

        assertTrue(
            "ProviderHttp no longer consults EndpointPolicy. With the manifest " +
                "permitting cleartext, nothing else would stop a request going " +
                "to a public address over http.",
            "EndpointPolicy.check(" in source.readText(),
        )
    }

    /**
     * The property all of the above exists to protect, stated once as a fact
     * rather than as a structure: the switch widens cleartext to the user's own
     * network, never to the internet.
     */
    @Test
    fun `the developer switch never reaches a public address`() {
        listOf(
            "http://api.openai.com/v1/",
            "http://8.8.8.8/v1/",
            "http://172.32.0.1/v1/",
            "http://11.0.0.1/v1/",
        ).forEach { url ->
            // Refused, not refused-for-a-particular-reason. A name and a
            // routable IP are turned away for different reasons and with
            // different advice; what this test is about is that neither is
            // sent to. EndpointPolicyTest covers which message each one gets.
            val verdict = EndpointPolicy.check(url, allowCleartext = true)
            assertTrue(
                "$url is on the internet and must be refused even with the " +
                    "developer setting on: $verdict",
                verdict is EndpointVerdict.Refused,
            )
        }
    }

    private companion object {
        val IGNORED_DIRS = setOf("build", ".git", ".gradle", "design-system", "dataset")
    }
}
