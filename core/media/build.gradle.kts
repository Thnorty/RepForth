plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.repforth.core.media"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))

    // Coil and media networking (§9, §18)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
