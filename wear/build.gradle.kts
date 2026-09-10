plugins {
    alias(libs.plugins.repforth.wear.application)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
    // Applies unchanged to an application module now. It used to configure a
    // `LibraryExtension` and could not; moving the two things every rendering
    // module needs into the compose plugin is what freed it, and this is the
    // second module to benefit.
    alias(libs.plugins.repforth.android.screenshot)
}

android {
    namespace = "com.repforth.wear"

    defaultConfig {
        // §11: the same application id as the phone. Wear distribution treats
        // the two as one listing, and the pairing the Data Layer needs is
        // established by that identity plus a shared signing identity.
        applicationId = "com.repforth"

        // Both declared in build-logic's AppVersion.kt: §11 requires the two
        // artifacts to differ in code and agree on name, and a number that must
        // match in two files is one that eventually will not.
        versionCode = WEAR_VERSION_CODE
        versionName = APP_VERSION_NAME
    }
}

dependencies {
    // The only thing shared with the phone, and deliberately the only thing:
    // §11 gives the watch no history, no AI client and no key, so it has no
    // reason to see core:user-data, core:ai or core:secrets.
    implementation(project(":core:wear-protocol"))

    // The app's palette, faces and numeric scale. Not `core:designsystem`:
    // that module exposes phone Material 3 with `api`, which would put a
    // second, clashing MaterialTheme on this classpath and the phone's
    // Material into the watch APK. The tokens were split out for this.
    implementation(project(":core:designtokens"))

    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // §3: the ongoing-activity entry that brings someone back from the watch
    // face. `core-ktx` comes with it for NotificationCompat, which is how the
    // notification the activity decorates is built.
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    // Unit-test classpath only. core:testing exposes JUnit with `api`, so it
    // must never reach an APK -- this module has no androidTest, and adding
    // one would need its own fixtures.
    testImplementation(project(":core:testing"))

    // Robolectric, Roborazzi and the compose test rule all arrive with the
    // screenshot plugin above, so nothing is named here twice.
}
