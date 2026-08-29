plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.feature.history"
}

dependencies {
    implementation(project(":core:designsystem"))

    // Exercise names for the muscles and lifts a history mentions.
    implementation(project(":core:exercise-data"))

    // Completed sessions, and the statistics over them, which arrive with
    // core:workout through core:user-data's `api`.
    implementation(project(":core:user-data"))
    implementation(project(":core:common"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
