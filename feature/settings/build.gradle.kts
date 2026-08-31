plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.feature.settings"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:media"))
    implementation(project(":core:user-data"))
    implementation(project(":core:exercise-data"))

    // Provider settings and the key that goes with them (§8). This module talks
    // to core:ai, never to core:secrets directly — one reader of the key, and
    // it is not a screen.
    implementation(project(":core:ai"))

    // Export, import and the two deletes. Finished and, until this screen, with
    // nothing able to call it.
    implementation(project(":core:transfer"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
