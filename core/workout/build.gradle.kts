plugins {
    alias(libs.plugins.repforth.android.library)
}

android {
    namespace = "com.repforth.core.workout"
}

dependencies {
    api(project(":core:model"))
    // TimeSource only. The engine never reads a clock statically, which is what
    // lets every transition in §10 be tested with no device and no waiting.
    api(project(":core:common"))

    testImplementation(libs.junit)
}
