plugins {
    alias(libs.plugins.repforth.android.application)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.kotlin.serialization)
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
    // core:designsystem exposes the Compose stack via `api`, so nothing here
    // re-declares compose-ui / material3 / the BOM.
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    // Only to answer "has onboarding happened?" — the app shell reads the
    // profile's existence, never its contents.
    implementation(project(":core:user-data"))
    implementation(project(":feature:builder"))
    implementation(project(":feature:exercises"))
    implementation(project(":feature:history"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:session"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
}
