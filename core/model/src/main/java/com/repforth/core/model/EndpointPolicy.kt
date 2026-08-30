package com.repforth.core.model

import java.net.URI
import java.net.URISyntaxException

/** Why a base URL was refused, in terms the settings screen can explain. */
enum class EndpointRefusal {
    /** Nothing typed yet. */
    BLANK,

    /** Not a URL at all, or one with no host. */
    MALFORMED,

    /**
     * A bare host, with no `https://` in front of it.
     *
     * Told apart from [MALFORMED] because it is the likeliest thing anyone
     * types — a machine name copied from somewhere that did not include the
     * scheme — and "that is not a web address" is a poor reply to something
     * that very nearly is one. Nothing is assumed on the user's behalf: which
     * scheme it should be is exactly the security-relevant part of the answer.
     */
    MISSING_SCHEME,

    /** A scheme this app will not speak — `ftp://`, `file://`, and friends. */
    UNSUPPORTED_SCHEME,

    /** `http://`, with the developer setting off. */
    CLEARTEXT_NOT_ALLOWED,

    /** `http://` to a public address, which stays refused however the switch is set. */
    CLEARTEXT_NOT_LOCAL,

    /**
     * `http://` to a name rather than a numeric address.
     *
     * Its own reason because the fix is specific and the user would otherwise
     * be told their own network is not their own network.
     */
    CLEARTEXT_NEEDS_ADDRESS,

    /** Credentials in the URL, which would be stored in plain text. */
    EMBEDDED_CREDENTIALS,
}

sealed interface EndpointVerdict {
    /** [normalized] has a trailing slash, so paths can be appended blindly. */
    data class Allowed(val normalized: String) : EndpointVerdict

    data class Refused(val reason: EndpointRefusal) : EndpointVerdict
}

/**
 * Decides whether the app will talk to a base URL (§8).
 *
 * §8: "Allow only `https://` endpoints by default. A developer setting may
 * permit cleartext `http://` for a loopback/LAN model such as Ollama or
 * LM Studio, with a prominent warning and narrowly scoped Android
 * network-security configuration."
 *
 * **This file is the narrow scope, and it has to be, because the manifest
 * cannot be.** A network-security configuration lists hosts; it has no way to
 * say "any address on the user's own network", and the user's LAN address is
 * not knowable when the app is built. So the platform config permits cleartext
 * and this decides, per address, whether the app will actually send — which is
 * the only place the real rule can be written.
 *
 * That trade moves a platform guarantee into application code, so the app has
 * to be the only door. `CleartextGuardTest` holds that: one module depends on
 * an HTTP client, one file makes calls with it, and that file consults this
 * before every request.
 *
 * The rule itself, in one line: **cleartext only to a numeric address on a
 * private network, and only when the user has switched it on.** A public
 * address over `http://` puts the user's API key on the wire in the clear, and
 * that is the mistake in this file that cannot be taken back.
 */
object EndpointPolicy {

    fun check(url: String, allowCleartext: Boolean): EndpointVerdict {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return EndpointVerdict.Refused(EndpointRefusal.BLANK)
        if ("://" !in trimmed) {
            return EndpointVerdict.Refused(EndpointRefusal.MISSING_SCHEME)
        }

        val uri = try {
            URI(trimmed)
        } catch (e: URISyntaxException) {
            return EndpointVerdict.Refused(EndpointRefusal.MALFORMED)
        }

        val host = uri.host
        if (host.isNullOrBlank()) return EndpointVerdict.Refused(EndpointRefusal.MALFORMED)

        // A URL may carry `user:password@host`. OkHttp would happily send it,
        // and it would be sitting in plain-text DataStore forever.
        if (uri.userInfo != null) {
            return EndpointVerdict.Refused(EndpointRefusal.EMBEDDED_CREDENTIALS)
        }

        return when (uri.scheme?.lowercase()) {
            "https" -> EndpointVerdict.Allowed(trimmed.withTrailingSlash())
            "http" -> checkCleartext(host, trimmed, allowCleartext)
            else -> EndpointVerdict.Refused(EndpointRefusal.UNSUPPORTED_SCHEME)
        }
    }

    private fun checkCleartext(
        host: String,
        url: String,
        allowCleartext: Boolean,
    ): EndpointVerdict = when {
        !allowCleartext -> EndpointVerdict.Refused(EndpointRefusal.CLEARTEXT_NOT_ALLOWED)
        isPrivate(host) -> EndpointVerdict.Allowed(url.withTrailingSlash())
        // Told apart so the message can say "type the numeric address" rather
        // than "that is not on your network", which for `http://my-pc.local`
        // would be both unhelpful and, as far as the user knows, untrue.
        !isNumeric(host) -> EndpointVerdict.Refused(EndpointRefusal.CLEARTEXT_NEEDS_ADDRESS)
        else -> EndpointVerdict.Refused(EndpointRefusal.CLEARTEXT_NOT_LOCAL)
    }

    /**
     * Loopback, the three private IPv4 ranges, and link-local.
     *
     * These are the addresses that cannot be routed across the internet, so a
     * request to one of them stays on the wire between the phone and something
     * the user physically has. That is the property worth having; "192.168" is
     * just how it is spelled.
     *
     * **Hostnames are deliberately not resolved**, and `localhost` is the only
     * name accepted. A DNS lookup here would be a network call inside a
     * validation function, and one an attacker who controls the name can answer
     * differently the second time — the classic rebinding shape, with the API
     * key as the prize. A numeric address is checkable without asking anyone.
     */
    private fun isPrivate(host: String): Boolean {
        val bare = host.trim('[', ']').lowercase()
        if (bare == "localhost") return true

        val octets = bare.split('.')
        if (octets.size != 4) return false
        val numbers = octets.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false

        return when {
            numbers[0] == 127 -> true
            numbers[0] == 10 -> true
            numbers[0] == 192 && numbers[1] == 168 -> true
            numbers[0] == 172 && numbers[1] in 16..31 -> true
            // 169.254/16 — what a device gives itself when there is no DHCP,
            // which is exactly the phone-to-laptop cable case.
            numbers[0] == 169 && numbers[1] == 254 -> true
            else -> false
        }
    }

    /** Four dot-separated numbers. Not necessarily a *valid* address. */
    private fun isNumeric(host: String): Boolean {
        val octets = host.trim('[', ']').split('.')
        return octets.size == 4 && octets.all { it.toIntOrNull() != null }
    }

    private fun String.withTrailingSlash() = if (endsWith("/")) this else "$this/"
}
