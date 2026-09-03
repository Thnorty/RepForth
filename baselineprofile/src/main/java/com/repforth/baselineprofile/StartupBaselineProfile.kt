package com.repforth.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records what runs when the app is opened cold.
 *
 * A baseline profile is a list of the classes and methods worth compiling ahead
 * of time. Android applies it at install, so the first launch executes compiled
 * code rather than interpreting it and then noticing. It changes nothing
 * visible; it is measured only in milliseconds nobody spent waiting.
 *
 * **The journey stops at the first frame, on purpose.** Every iteration of this
 * runs against a freshly installed app, and a freshly installed RepForth opens
 * onboarding — the one screen a user sees exactly once. Driving through it would
 * spend the profile on seven questions nobody revisits, while the code that
 * actually matters has already run by then: the `Application`, the Hilt graph,
 * the first DataStore read, Compose starting up, the theme resolving, the nav
 * host composing. That path is identical whether the first frame is onboarding
 * or Today.
 *
 * **Generating this installs and uninstalls the app repeatedly.** On a phone
 * with real data in it, that data is gone — the same warning `AGENTS.md`
 * carries for `connectedAndroidTest`, for the same reason. The module is
 * configured to use a managed emulator rather than whatever is plugged in, so
 * that this cannot happen by accident.
 *
 *   ./gradlew :app:generatePlaceholderBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = PACKAGE_NAME,
        // The default is a warm-up plus several passes; left alone because a
        // profile that disagrees between runs is worse than a slower one.
        profileBlock = {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        },
    )

    private companion object {
        /** §21 still defers the final applicationId; this tracks it. */
        const val PACKAGE_NAME = "com.repforth"
    }
}
