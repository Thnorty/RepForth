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

    /** `http://` to somewhere that is not this device, which stays refused. */
    CLEARTEXT_NOT_LOOPBACK,

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
                !isLoopback(host) -> EndpointVerdict.Refused(EndpointRefusal.CLEARTEXT_NOT_LOOPBACK)
                else -> EndpointVerdict.Allowed(trimmed.withTrailingSlash())
            }

            else -> EndpointVerdict.Refused(EndpointRefusal.UNSUPPORTED_SCHEME)
        }
    }

    /**
     * This device, and the emulator's alias for the machine hosting it.
     *
     * Hostnames are deliberately not resolved. A DNS lookup here would be a
     * network call inside a validation function, and one that an attacker
     * controlling the name could answer differently the second time — the
     * classic rebinding shape. `localhost` and a literal loopback address are
     * checkable without asking anyone, so those are the only things allowed.
     *
     * `10.0.2.2` is the Android emulator's route to its host's loopback. It is
     * not loopback from the device's point of view, and it is in a private
     * range this policy otherwise refuses — it is here so that a model server
     * running on the development machine is reachable from an emulator, which
     * is the only way this path can be exercised without hardware.
     */
    private fun isLoopback(host: String): Boolean {
        val bare = host.trim('[', ']').lowercase()
        // No IPv6 literal. A network-security configuration names hosts, and
        // `[::1]` is not one it accepts — permitting it here would be a policy
        // the platform then refuses. `localhost` is the spelling that works.
        if (bare == "localhost" || bare == EMULATOR_HOST) return true

        val octets = bare.split('.')
        if (octets.size != 4) return false
        val numbers = octets.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false

        return numbers[0] == 127
    }

    /**
     * The hosts cleartext may reach, which `res/xml/network_security_config.xml`
     * has to repeat because the platform can only read it from XML.
     *
     * Exposed so the guard test can compare the two rather than trusting that
     * whoever edits one remembers the other.
     */
    val CLEARTEXT_HOSTS = listOf("localhost", "127.0.0.1", EMULATOR_HOST)

    private const val EMULATOR_HOST = "10.0.2.2"

    private fun String.withTrailingSlash() = if (endsWith("/")) this else "$this/"
}
