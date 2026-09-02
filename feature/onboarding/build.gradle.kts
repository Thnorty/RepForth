plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.repforth.android.screenshot)
}

android {
    namespace = "com.repforth.feature.onboarding"
}

dependencies {
    // The Compose stack and core:model arrive through core:designsystem's `api`.
    implementation(project(":core:designsystem"))

    // For Equipment.labelRes and Muscle.labelRes: the catalog owns the display
    // names of the vocabulary, and onboarding asks about the same vocabulary the
    // catalog filters by. Two lists of equipment names would drift.
    implementation(project(":core:exercise-data"))

    // The one door to the profile this screen exists to write.
    implementation(project(":core:user-data"))

    // The notification permission is asked for here, with a reason beside it.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
