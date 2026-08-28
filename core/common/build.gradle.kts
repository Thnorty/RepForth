plugins {
    alias(libs.plugins.repforth.android.library)
    // For javax.inject only; this module declares no Hilt component of its own.
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.core.common"
}

dependencies {
    testImplementation(libs.junit)
}
