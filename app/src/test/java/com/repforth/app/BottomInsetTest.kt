package com.repforth.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app shell must move out of the keyboard's way itself.
 *
 * `enableEdgeToEdge()` turns off decor-fits-system-windows, and with it the
 * automatic window resize that used to happen when the IME opened. Nothing
 * replaces it: the window stays full height, the keyboard is drawn over the
 * bottom of it, and anything pinned there is simply covered.
 *
 * Found on a device, and it looked like nothing at all: the builder's Save
 * button, enabled and reachable in the semantics tree, sat underneath the
 * keyboard for as long as the name field held focus — so a workout could be
 * built and never saved. No JVM test sees this, because no JVM test has a
 * keyboard.
 *
 * The assertion names `safeDrawing`, not `imePadding`, on purpose. safeDrawing
 * is the union of the IME, the navigation bar and the display cutout; the phone
 * this was found on uses gesture navigation and reserves no bottom inset, so
 * the narrower fix would look identical here and fail on a phone with a
 * three-button bar.
 *
 * Asserted on the source rather than through a Compose test because the IME is
 * a platform surface: Robolectric has no keyboard to open, and an instrumented
 * test would have to negotiate with whichever IME the device happens to ship.
 * This is the same trade `BackupPolicyTest` makes — a cheap guard on a value
 * whose absence is silent.
 */
class BottomInsetTest {

    @Test
    fun `the shell pads its own bottom edge`() {
        val shell = File("src/main/java/com/repforth/app/ui/RepForthApp.kt")
        assertTrue(
            "Expected ${shell.absolutePath} to exist; is the unit test running " +
                "from the module dir?",
            shell.exists(),
        )

        val source = shell.readText()

        assertTrue(
            "The Scaffold in RepForthApp no longer pads its bottom edge with " +
                "safeDrawing. The app is edge-to-edge and Scaffold contributes " +
                "no bottom inset on screens without a bottom bar, so every " +
                "pinned footer goes under the navigation bar, and behind the " +
                "keyboard when one opens — still enabled, impossible to tap.",
            "WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)" in source,
        )
    }
}
