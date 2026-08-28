plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.room)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.core.database"
}

dependencies {
    // Entities map to core:model types; nothing maps the other way.
    api(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.kotlinx.serialization.json)
}
