plugins {
    alias(libs.plugins.repforth.baselineprofile.producer)
}

android {
    namespace = "com.repforth.baselineprofile"

    // The app whose startup is recorded. Not a dependency in the ordinary
    // sense: this module builds its own APK and drives that one from outside
    // the process, which is the only way to watch a cold start.
    targetProjectPath = ":app"
}
