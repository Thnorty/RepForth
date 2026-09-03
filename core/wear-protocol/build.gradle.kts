plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.repforth.core.wearprotocol"
}

dependencies {
    // The wire format. This module is the only place the phone and the watch
    // agree on anything, so the serialization runtime is part of what it is.
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
