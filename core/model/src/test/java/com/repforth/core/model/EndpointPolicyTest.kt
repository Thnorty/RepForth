package com.repforth.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which endpoints the app will send an API key to (§8).
 *
 * The consequence of getting this wrong is not a broken screen. `http://` to a
 * public host puts the user's key on the wire in clear text, to be read by
 * anything between the phone and the server — and it would work, so nothing
 * would ever look wrong.
 *
 * So the cases below are mostly about refusing, and the one that matters most
 * is that turning the developer setting on does not turn the rule off: it
 * widens cleartext to local addresses only.
 */
class EndpointPolicyTest {

    @Test
    fun `https is allowed`() {
        val verdict = EndpointPolicy.check("https://api.example.com/v1", allowCleartext = false)

        assertTrue("$verdict", verdict is EndpointVerdict.Allowed)
    }

    @Test
    fun `a trailing slash is added so paths can be appended`() {
        val verdict = EndpointPolicy.check("https://api.example.com/v1", allowCleartext = false)

        assertEquals(
            "https://api.example.com/v1/",
            (verdict as EndpointVerdict.Allowed).normalized,
        )
    }

    @Test
    fun `an existing trailing slash is left alone`() {
        val verdict = EndpointPolicy.check("https://api.example.com/v1/", allowCleartext = false)

        assertEquals(
            "https://api.example.com/v1/",
            (verdict as EndpointVerdict.Allowed).normalized,
        )
    }

    @Test
    fun `cleartext is refused by default`() {
        val verdict = EndpointPolicy.check("http://localhost:11434", allowCleartext = false)

        assertEquals(
            EndpointRefusal.CLEARTEXT_NOT_ALLOWED,
            (verdict as EndpointVerdict.Refused).reason,
        )
    }

    /**
     * The developer setting exists for Ollama and LM Studio, which is what §8
     * names. This device, and nothing else.
     */
    @Test
    fun `cleartext to loopback is allowed once the setting is on`() {
        listOf(
            "http://localhost:11434",
            "http://127.0.0.1:1234",
            "http://127.0.0.53:8080",
            // The emulator's route to its host, which is the only way this path
            // can be exercised without a phone.
            "http://10.0.2.2:11434",
        ).forEach { url ->
            assertTrue(
                "$url should be reachable with the developer setting on",
                EndpointPolicy.check(url, allowCleartext = true) is EndpointVerdict.Allowed,
            )
        }
    }

    /**
     * §8 mentions LAN as well as loopback, and this app deliberately does not
     * do LAN. A network-security configuration names hosts, not ranges, so
     * "any 192.168.x.x" cannot be permitted at the platform level — and a policy
     * that allowed what the platform refuses would fail at the socket with an
     * error nobody could explain.
     */
    @Test
    fun `a private network address is not treated as loopback`() {
        listOf(
            "http://192.168.1.42:11434",
            "http://10.0.0.5:8080",
            "http://172.16.4.1:8000",
        ).forEach { url ->
            val verdict = EndpointPolicy.check(url, allowCleartext = true)
            assertTrue(
                "$url is on the network, not on this device. The platform will " +
                    "refuse it, so this policy has to as well: $verdict",
                verdict is EndpointVerdict.Refused,
            )
        }
    }

    /**
     * The test this file exists for.
     *
     * Watched failing with the `isLoopback` check removed from the `http` branch:
     * every one of these was allowed, which is a key sent in clear text over
     * the internet on the strength of a checkbox labelled "for local models".
     */
    @Test
    fun `cleartext to a public address stays refused with the setting on`() {
        listOf(
            "http://api.example.com/v1",
            "http://8.8.8.8/v1",
            "http://172.32.0.1/v1",
            "http://11.0.0.1/v1",
            "http://192.169.1.1/v1",
            "http://126.255.255.255/v1",
        ).forEach { url ->
            val verdict = EndpointPolicy.check(url, allowCleartext = true)
            // Asserted as a type before it is cast, so removing the locality
            // check reports the endpoint that was allowed rather than a
            // ClassCastException that says nothing about what broke.
            assertTrue(
                "$url is not a local address. Allowing it sends the user's API " +
                    "key over the internet in clear text.",
                verdict is EndpointVerdict.Refused,
            )
            assertEquals(
                EndpointRefusal.CLEARTEXT_NOT_LOOPBACK,
                (verdict as EndpointVerdict.Refused).reason,
            )
        }
    }

    @Test
    fun `other schemes are refused`() {
        listOf("ftp://example.com", "file:///etc/passwd", "ws://example.com").forEach { url ->
            val verdict = EndpointPolicy.check(url, allowCleartext = true)
            assertTrue(
                "$url should not be reachable",
                verdict is EndpointVerdict.Refused,
            )
        }
    }

    /**
     * `https://user:password@host` would be stored in plain-text DataStore and
     * sent on every request, which is a credential in exactly the place §20
     * says one must never be.
     */
    @Test
    fun `credentials in the url are refused`() {
        val verdict = EndpointPolicy.check(
            "https://user:secret@api.example.com/v1",
            allowCleartext = false,
        )

        assertEquals(
            EndpointRefusal.EMBEDDED_CREDENTIALS,
            (verdict as EndpointVerdict.Refused).reason,
        )
    }

    @Test
    fun `blank and malformed are told apart`() {
        assertEquals(
            "Nothing typed yet is not the same as nonsense typed",
            EndpointRefusal.BLANK,
            (EndpointPolicy.check("   ", allowCleartext = false) as EndpointVerdict.Refused).reason,
        )
        assertEquals(
            EndpointRefusal.MALFORMED,
            (
                EndpointPolicy.check("not a url", allowCleartext = false)
                    as EndpointVerdict.Refused
                ).reason,
        )
    }

    /**
     * A hostname is never resolved to decide whether it is local.
     *
     * Resolving would be a network call inside a validation function, and one
     * an attacker who controls the name can answer differently the second time
     * — DNS rebinding, with the user's API key as the prize. Names that are not
     * `localhost` are simply not local.
     */
    @Test
    fun `a hostname that is not localhost is not treated as loopback`() {
        val verdict = EndpointPolicy.check("http://my-nas.lan:11434", allowCleartext = true)

        assertTrue(
            "A name this app cannot check without asking DNS is not loopback: $verdict",
            verdict is EndpointVerdict.Refused,
        )
        assertEquals(
            EndpointRefusal.CLEARTEXT_NOT_LOOPBACK,
            (verdict as EndpointVerdict.Refused).reason,
        )
    }
}
