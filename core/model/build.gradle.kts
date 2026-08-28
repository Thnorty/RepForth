plugins {
    alias(libs.plugins.repforth.android.library)
}

android {
    namespace = "com.repforth.core.model"
}

dependencies {
    testImplementation(libs.junit)
    // Parsing only, so no serialization plugin is needed here. Android's own
    // org.json is a stub in unit tests and throws "not mocked".
    testImplementation(libs.kotlinx.serialization.json)
}
