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

    // §11: the watch is a remote for the workout this module runs, so the
    // bridge lives beside the service that keeps it alive rather than in a
    // module of its own that would need the controller anyway.
    implementation(project(":core:wear-sync"))
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
