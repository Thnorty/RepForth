plugins {
    alias(libs.plugins.repforth.android.application)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.repforth.baselineprofile.consumer)
}

android {
    namespace = "com.repforth.app"

    defaultConfig {
        // §21: final applicationId is still deferred. This is a placeholder.
        applicationId = "com.repforth"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    baselineProfile(project(":baselineprofile"))

    // core:designsystem exposes the Compose stack via `api`, so nothing here
    // re-declares compose-ui / material3 / the BOM.
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:media"))
    // Only to answer "has onboarding happened?" — the app shell reads the
    // profile's existence, never its contents.
    implementation(project(":core:user-data"))
    implementation(project(":feature:builder"))
    implementation(project(":feature:exercises"))
    implementation(project(":feature:history"))
    implementation(project(":feature:home"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:session"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // The shell hosts one view model of its own: the gate that asks about a
    // running workout before navigating into a new one.
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)

    // §17: instrumentation. These run on a device, against the real navigation
    // graph and the real Hilt graph — which is where every defect found by hand
    // so far has lived.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.datastore.preferences)

    // `implementation` dependencies reach androidTest at runtime but not at
    // compile time, so a test that seeds the database through the real
    // repositories has to name them again. Not a duplicate of the app's own
    // dependency -- a different configuration of the same project.
    androidTestImplementation(project(":core:model"))
    androidTestImplementation(project(":core:user-data"))
    androidTestImplementation(project(":feature:session"))
    // Coach's generation binding is replaced with a fixture on a test device;
    // see TestGenerationModule for why it has to be.
    androidTestImplementation(project(":core:ai"))
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(project(":core:testing"))
}
