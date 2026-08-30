package com.repforth.app

import com.repforth.core.model.EndpointPolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cleartext allowlist exists twice, and the two copies must agree (§8).
 *
 * This is the third forced duplicate in the project, and it is forced for the
 * same reason as `rf_launch_background`: the platform reads its answer from XML
 * before any Kotlin runs. `EndpointPolicy` decides whether the app will *send*
 * to an address; `network_security_config.xml` decides whether Android will
 * *carry* it. Neither can consult the other.
 *
 * The failure mode when they drift is nasty in both directions. If the XML is
 * wider, the app permits cleartext to a host its own policy meant to refuse. If
 * the policy is wider, the user configures an address the app accepts, and every
 * request fails at the socket with an error that mentions neither this file nor
 * the reason.
 *
 * Watched failing by adding `192.168.1.1` to the XML alone.
 */
class NetworkSecurityConfigTest {

    @Test
    fun `the manifest permits cleartext to exactly the hosts the policy allows`() {
        val config = File("src/main/res/xml/network_security_config.xml")
        assertTrue(
            "Expected ${config.absolutePath} to exist. If the network security " +
                "config was removed, cleartext to a local model server stops " +
                "working and §8's developer setting becomes a switch that does " +
                "nothing.",
            config.exists(),
        )
        val text = config.readText()

        val permitted = DOMAIN.findAll(text).map { it.groupValues[1].trim() }.toList()

        assertEquals(
            "The cleartext allowlist in network_security_config.xml has drifted " +
                "from EndpointPolicy.CLEARTEXT_HOSTS. Both have to say the same " +
                "thing: one decides what the app will send, the other what the " +
                "platform will carry.",
            EndpointPolicy.CLEARTEXT_HOSTS,
            permitted,
        )
    }

    /**
     * Cleartext must be off by default, so an address that is not on the list
     * above is refused by the platform rather than merely by this app's policy.
     */
    @Test
    fun `everything not on the list is refused by the platform`() {
        val text = File("src/main/res/xml/network_security_config.xml").readText()

        assertTrue(
            "The base config must refuse cleartext. Without it, the domain " +
                "list stops being an allowlist and becomes decoration.",
            """<base-config\s+cleartextTrafficPermitted="false"""".toRegex()
                .containsMatchIn(text),
        )
    }

    private companion object {
        val DOMAIN = Regex("""<domain[^>]*>([^<]+)</domain>""")
    }
}
