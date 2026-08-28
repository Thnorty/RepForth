package com.repforth.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Test

/**
 * The launch background exists twice — once as [Tone.N6] in Kotlin, once as
 * `rf_launch_background` in XML, because the window needs a colour before
 * Compose is running. That duplication is forced by the platform, so it is
 * guarded here instead of being left to drift silently.
 */
class LaunchBackgroundTest {

    private fun Color.toHex(): String = String.format(
        Locale.ROOT,
        "#%02X%02X%02X",
        (red * 255f).roundToInt(),
        (green * 255f).roundToInt(),
        (blue * 255f).roundToInt(),
    )

    @Test
    fun `launch background xml matches the surface-app token`() {
        val xml = File("src/main/res/values/colors.xml")
        assertTrue(
            "Expected ${xml.absolutePath} to exist; is the unit test running from the module dir?",
            xml.exists(),
        )

        val declared = Regex("""<color name="rf_launch_background">(#[0-9A-Fa-f]{6})</color>""")
            .find(xml.readText())
            ?.groupValues
            ?.get(1)
            ?.uppercase(Locale.ROOT)

        assertEquals(
            "rf_launch_background has drifted from Tone.N6 (--surface-app, dark). " +
                "Update the XML to match Color.kt.",
            Tone.N6.toHex(),
            declared,
        )
    }
}
