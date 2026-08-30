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

    // Coach's optional provider path. The feature sees the orchestration
    // boundary and typed outcomes, never provider HTTP or secret storage.
    implementation(project(":core:ai"))

    // The catalog, for picking exercises and resolving a plan's names.
    implementation(project(":core:datastore"))
    implementation(project(":core:exercise-data"))
    implementation(project(":core:media"))

    // The one door to plans and the profile.
    implementation(project(":core:user-data"))

    // Coach's local constraint boundary: provider candidates and answers are
    // filtered and validated here before editable rows reach the builder.
    implementation(project(":core:rules"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
