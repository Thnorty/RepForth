plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.repforth.android.instrumentation)
}

android {
    namespace = "com.repforth.core.secrets"
}

dependencies {
    implementation(libs.tink.android)

    // The real store needs the Android Keystore, which does not exist on the
    // JVM, so the tests that prove it works are instrumentation tests.
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
