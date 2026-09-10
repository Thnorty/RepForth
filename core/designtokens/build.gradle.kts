plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
}

android {
    namespace = "com.repforth.core.designtokens"
}

dependencies {
    // Compose, but deliberately *not* Material 3.
    //
    // That absence is the whole reason this module exists. `core:designsystem`
    // exposes phone Material 3 with `api`, and the watch renders with
    // `androidx.wear.compose.material3` -- a different library that declares its
    // own `MaterialTheme`, `Text` and `Button`. A watch that depended on
    // `core:designsystem` to reach the palette would drag the phone's Material
    // into its APK and put two clashing `MaterialTheme`s on one classpath.
    //
    // So the tokens live here, where both platforms can read them, and each
    // builds its own Material theme on top. `RepForthShapes` and
    // `RepForthTypography` stay in `core:designsystem` because they are phone
    // Material 3 types; the values they are assembled from are here.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)

    testImplementation(libs.junit)
}
