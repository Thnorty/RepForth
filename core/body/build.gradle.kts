plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.core.body"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))

    // Deliberately not core:testing: that module depends on this one, so its
    // fakes would make a cycle (AGENTS.md). This module's tests use their own.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
