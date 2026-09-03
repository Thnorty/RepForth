plugins {
    alias(libs.plugins.repforth.wear.application)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.wear"

    defaultConfig {
        // §11: the same application id as the phone. Wear distribution treats
        // the two as one listing, and the pairing the Data Layer needs is
        // established by that identity plus a shared signing identity.
        applicationId = "com.repforth"

        // §11 also requires the two artifacts to have *different* version
        // codes. 1000 apart rather than +1, so the watch's number cannot be
        // mistaken for the next phone release.
        versionCode = 1001
        versionName = "0.1.0"
    }
}

dependencies {
    // The only thing shared with the phone, and deliberately the only thing:
    // §11 gives the watch no history, no AI client and no key, so it has no
    // reason to see core:user-data, core:ai or core:secrets.
    implementation(project(":core:wear-protocol"))

    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    // Unit-test classpath only. core:testing exposes JUnit with `api`, so it
    // must never reach an APK -- this module has no androidTest, and adding
    // one would need its own fixtures.
    testImplementation(project(":core:testing"))
}
