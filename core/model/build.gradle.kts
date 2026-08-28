plugins {
    alias(libs.plugins.repforth.android.library)
}

android {
    namespace = "com.repforth.core.model"
}

dependencies {
    testImplementation(libs.junit)
}
