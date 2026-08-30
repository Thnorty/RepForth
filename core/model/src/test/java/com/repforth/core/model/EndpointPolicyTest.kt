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
     * names — and those usually run on the user's desktop, not on the phone.
     *
     * So the whole of the user's own network is reachable: loopback for a
     * server on the device, the three private ranges for one on the LAN, and
     * link-local for a phone plugged straight into a laptop with no DHCP.
     */
    @Test
    fun `cleartext to the user's own network is allowed once the setting is on`() {
        listOf(
            "http://localhost:11434",
            "http://127.0.0.1:1234",
            // The ordinary case: Ollama on a desktop, reached over Wi-Fi.
            "http://192.168.1.42:11434",
            "http://10.0.0.5:8080",
            "http://172.16.4.1:8000",
            "http://172.31.255.254:8000",
            "http://169.254.10.2:11434",
            // The Android emulator's route to its host.
            "http://10.0.2.2:11434",
        ).forEach { url ->
            assertTrue(
                "$url should be reachable with the developer setting on",
                EndpointPolicy.check(url, allowCleartext = true) is EndpointVerdict.Allowed,
            )
        }
    }

    /**
     * A name is refused with its own reason, so the message can say what to do.
     *
     * `http://my-desktop.local` is almost certainly on the user's network, and
     * this app still will not send to it: checking would mean a DNS lookup
     * inside a validation function, answerable differently the second time by
     * whoever controls the name. Telling the user "that is not on your network"
     * would be unhelpful and, as far as they know, false — so the refusal says
     * "type the numeric address" instead.
     */
    @Test
    fun `a name is refused with advice rather than a contradiction`() {
        listOf(
            "http://my-desktop.local:11434",
            "http://ollama:11434",
            "http://my-nas.lan:11434",
        ).forEach { url ->
            val verdict = EndpointPolicy.check(url, allowCleartext = true)
            assertTrue("$url: $verdict", verdict is EndpointVerdict.Refused)
            assertEquals(
                url,
                EndpointRefusal.CLEARTEXT_NEEDS_ADDRESS,
                (verdict as EndpointVerdict.Refused).reason,
            )
        }
    }

    /**
     * The test this file exists for.
     *
     * Watched failing with the `isPrivate` check removed from the `http` branch:
     * every one of these was allowed, which is a key sent in clear text over
     * the internet on the strength of a checkbox labelled "for local models".
     */
    @Test
    fun `cleartext to a public address stays refused with the setting on`() {
        listOf(
            "http://8.8.8.8/v1",
            "http://172.32.0.1/v1",
            "http://11.0.0.1/v1",
            "http://192.169.1.1/v1",
            "http://126.255.255.255/v1",
            "http://9.255.255.255/v1",
            "http://172.15.0.1/v1",
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
                EndpointRefusal.CLEARTEXT_NOT_LOCAL,
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
        // Has a scheme and still no host, which is the only thing left that
        // MALFORMED means now that a bare name has its own answer.
        assertEquals(
            EndpointRefusal.MALFORMED,
            (
                EndpointPolicy.check("https://", allowCleartext = false)
                    as EndpointVerdict.Refused
                ).reason,
        )
        assertEquals(
            "Something with no scheme is missing one, not nonsense",
            EndpointRefusal.MISSING_SCHEME,
            (
                EndpointPolicy.check("not a url", allowCleartext = false)
                    as EndpointVerdict.Refused
                ).reason,
        )
    }

    /**
     * A bare machine name is refused, and told what it is missing.
     *
     * `laptop-tulpar` is a real thing to type — a Tailscale MagicDNS name, a
     * LAN hostname, whatever the machine is called — and the app deliberately
     * does not guess a scheme for it. Guessing `https` would quietly succeed
     * where the user meant a local `http` server; guessing `http` would be
     * worse. Which one it is, is exactly the security-relevant half of the
     * answer, so the user supplies it.
     */
    @Test
    fun `a bare host name says what it is missing`() {
        listOf("laptop-tulpar", "laptop-tulpar:11434", "192.168.1.42:11434").forEach { url ->
            val verdict = EndpointPolicy.check(url, allowCleartext = true)
            assertTrue("$url: $verdict", verdict is EndpointVerdict.Refused)
            assertEquals(
                url,
                EndpointRefusal.MISSING_SCHEME,
                (verdict as EndpointVerdict.Refused).reason,
            )
        }
    }

    /**
     * The same name with https in front of it is fine, with the cleartext
     * switch off — which is how a Tailscale or reverse-proxied server is
     * reached, and needs nothing turned on.
     */
    @Test
    fun `the same name over https is allowed with nothing switched on`() {
        assertTrue(
            EndpointPolicy.check("https://laptop-tulpar/v1/", allowCleartext = false)
                is EndpointVerdict.Allowed,
        )
    }

    /** A public name over http is refused as well, by the same switch. */
    @Test
    fun `a public name over cleartext is refused too`() {
        val verdict = EndpointPolicy.check("http://api.openai.com/v1/", allowCleartext = true)

        assertTrue("$verdict", verdict is EndpointVerdict.Refused)
    }

    /**
     * `localhost` is the one name accepted, because it cannot mean anything
     * else. Every other name would need a lookup to judge.
     */
    @Test
    fun `localhost is the only name that needs no lookup`() {
        assertTrue(
            EndpointPolicy.check("http://localhost:11434", allowCleartext = true)
                is EndpointVerdict.Allowed,
        )
    }

    /** https to a name is fine — TLS is what makes the name safe to trust. */
    @Test
    fun `a name over https needs no such care`() {
        assertTrue(
            EndpointPolicy.check("https://my-desktop.local/v1/", allowCleartext = false)
                is EndpointVerdict.Allowed,
        )
    }
}
