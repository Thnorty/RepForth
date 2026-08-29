package com.repforth.app

import android.app.Application
import android.content.Context
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
}
