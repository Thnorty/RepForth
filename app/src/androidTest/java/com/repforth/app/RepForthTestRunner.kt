package com.repforth.app

import android.app.Application
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps in the Application Hilt builds for tests.
 *
 * `RepForthApplication` is `@HiltAndroidApp`, which is right for the app and
 * wrong for a test: the test graph has to be assembled per test so that
 * bindings can be replaced. Without this the injected fields are simply never
 * set, which surfaces much later as a null with no explanation.
 */
class RepForthTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(classLoader, HiltTestApplication::class.java.name, context)

    /**
     * Stops the platform putting a crash or ANR dialog over the app.
     *
     * **This is what the keyboard flake actually was.** A test that waits for
     * the window to take focus failed with focus held by
     * `Window{... Application Not Responding: com.android.systemui}`, while
     * `mFocusedApp` was still `com.repforth/.app.MainActivity` — the app was the
     * focused *app* with a system dialog on top of it. An unfocused window
     * cannot raise a keyboard, so the test then waited out its whole budget on a
     * precondition that was never coming back.
     *
     * It is intermittent because it is SystemUI failing to draw a frame in time
     * on a busy emulator, not anything this app does; the two earlier
     * explanations — the AVD's hardware keyboard, and a dropped first tap — were
     * built from `dumpsys input_method`, which names its current client and
     * cannot see that a dialog owns the focus.
     *
     * Suppressing the dialog does not hide a failure of this app: an ANR in
     * `com.android.systemui` is the emulator's health, and a suite that any
     * system process can fail by being slow is measuring the machine. If this
     * app ever ANRs, the test that was driving it still fails.
     *
     * Set in `onStart` rather than `onCreate`: `uiAutomation` needs the
     * instrumentation to be connected, which `onCreate` is still doing, and
     * `onCreate` hands off to a worker thread that could start a test before
     * the setting landed.
     */
    override fun onStart() {
        shell("settings put global hide_error_dialogs 1")
        // Read back, because a write that did not land looks exactly like a
        // write that did until a test fails twenty minutes later with focus
        // held by a dialog this was meant to prevent. That has happened once.
        val applied = shell("settings get global hide_error_dialogs")
        check(applied == "1") {
            "hide_error_dialogs is [$applied] after being set to 1, so a system " +
                "crash or ANR dialog can still take focus from the app under test."
        }
        super.onStart()
    }

    /**
     * Runs a command as the shell user, which holds `WRITE_SECURE_SETTINGS`.
     *
     * The descriptor has to be drained and closed. Left unread, the command can
     * block on a full pipe and take the whole run with it.
     */
    private fun shell(command: String): String {
        val pipe: ParcelFileDescriptor = uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pipe).use {
            it.readBytes().decodeToString().trim()
        }
    }
}
