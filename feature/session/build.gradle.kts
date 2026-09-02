plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.repforth.android.screenshot)
}

android {
    namespace = "com.repforth.feature.session"
}

dependencies {
    implementation(project(":core:designsystem"))

    // Exercise names for the running workout; a session stores ids only.
    implementation(project(":core:exercise-data"))
    implementation(project(":core:media"))
    implementation(project(":core:datastore"))

    // Sessions, plans, and the TimeSource the engine runs on. core:user-data
    // exposes core:workout with `api`, so the engine arrives with it.
    implementation(project(":core:user-data"))
    implementation(project(":core:common"))

    // NotificationCompat and ContextCompat, for the ongoing workout
    // notification the foreground service posts.
    implementation(libs.androidx.core.ktx)

    // rememberLauncherForActivityResult, for the notification permission.
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
