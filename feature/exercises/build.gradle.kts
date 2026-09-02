plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.repforth.android.screenshot)
}

android {
    namespace = "com.repforth.feature.exercises"
}

dependencies {
    // The Compose stack arrives through core:designsystem's `api`, and the
    // catalog through core:exercise-data's. Neither is re-declared here.
    implementation(project(":core:designsystem"))
    implementation(project(":core:exercise-data"))
    implementation(project(":core:media"))
    implementation(project(":core:datastore"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
