plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.feature.builder"
}

dependencies {
    implementation(project(":core:designsystem"))

    // The catalog, for picking exercises and resolving a plan's names.
    implementation(project(":core:exercise-data"))

    // The one door to plans and the profile.
    implementation(project(":core:user-data"))

    // Coach's rules-only half (§3): the builder is where a generated plan
    // lands, because §12 makes Coach a mode inside the builder rather than a
    // screen of its own.
    implementation(project(":core:rules"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
