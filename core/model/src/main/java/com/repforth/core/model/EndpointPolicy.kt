package com.repforth.core.model

import java.net.URI
import java.net.URISyntaxException

/** Why a base URL was refused, in terms the settings screen can explain. */
enum class EndpointRefusal {
    /** Nothing typed yet. */
    BLANK,

    /** Not a URL at all, or one with no host. */
    MALFORMED,

    /** A scheme this app will not speak — `ftp://`, `file://`, and friends. */
    UNSUPPORTED_SCHEME,

    /** `http://`, with the developer setting off. */
    CLEARTEXT_NOT_ALLOWED,

    /** `http://` to somewhere that is not a local address, which stays refused. */
    CLEARTEXT_NOT_LOCAL,

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
 * **Narrowly scoped is enforced here, not only in the manifest.** The developer
 * setting does not mean "cleartext is fine now"; it means "cleartext to a
 * machine on this network is fine". So the switch alone is not enough —
 * `http://` to a public host is refused whether or not it is on. Getting that
 * wrong sends the user's API key across the internet unencrypted, which is the
 * one mistake in this file that cannot be taken back.
 *
 * This lives in `core:model` rather than in the provider layer so the settings
 * screen and the HTTP client apply the same rule. A check that exists only in
 * the text field is bypassed by the next caller — an import, a deep link — and
 * the second copy is where the two drift apart.
 */
object EndpointPolicy {

    fun check(url: String, allowCleartext: Boolean): EndpointVerdict {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return EndpointVerdict.Refused(EndpointRefusal.BLANK)

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
            "http" -> when {
                !allowCleartext -> EndpointVerdict.Refused(EndpointRefusal.CLEARTEXT_NOT_ALLOWED)
                !isLocal(host) -> EndpointVerdict.Refused(EndpointRefusal.CLEARTEXT_NOT_LOCAL)
                else -> EndpointVerdict.Allowed(trimmed.withTrailingSlash())
            }

            else -> EndpointVerdict.Refused(EndpointRefusal.UNSUPPORTED_SCHEME)
        }
    }

    /**
     * Loopback and the three private IPv4 ranges, plus IPv6 loopback.
     *
     * Hostnames are deliberately not resolved. A DNS lookup here would be a
     * network call inside a validation function, and one that an attacker
     * controlling the name could answer differently the second time — the
     * classic rebinding shape. A literal address or `localhost` is checkable
     * without asking anyone, so those are the only things allowed.
     */
    private fun isLocal(host: String): Boolean {
        val bare = host.trim('[', ']').lowercase()
        if (bare == "localhost" || bare == "::1") return true

        val octets = bare.split('.')
        if (octets.size != 4) return false
        val numbers = octets.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false

        return when {
            numbers[0] == 127 -> true
            numbers[0] == 10 -> true
            numbers[0] == 192 && numbers[1] == 168 -> true
            numbers[0] == 172 && numbers[1] in 16..31 -> true
            else -> false
        }
    }

    private fun String.withTrailingSlash() = if (endsWith("/")) this else "$this/"
}
