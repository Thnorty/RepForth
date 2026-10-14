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
    implementation(libs.androidx.core.ktx)

    // Deliberately not core:testing: that module depends on this one, so its
    // fakes would make a cycle (AGENTS.md). This module's tests use their own.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The photo store decodes and re-encodes real JPEGs, which needs Android's
    // graphics on the JVM.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
}
