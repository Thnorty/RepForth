plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.feature.home"
}

dependencies {
    implementation(project(":core:designsystem"))

    // Plans, sessions and the statistics over them. core:workout arrives with
    // core:user-data's `api`.
    implementation(project(":core:user-data"))
    implementation(project(":core:common"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
