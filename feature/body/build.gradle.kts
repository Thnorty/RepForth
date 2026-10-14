plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.repforth.android.screenshot)
}

android {
    namespace = "com.repforth.feature.body"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:body"))
    implementation(project(":core:common"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    // Local files only: photos never come from the network, so no Coil
    // network module here (NetworkBoundaryTest).
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
