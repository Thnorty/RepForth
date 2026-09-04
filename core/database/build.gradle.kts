plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.room)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.repforth.android.instrumentation)
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

    // A migration can only be proven against a real SQLite, so this is the one
    // thing in this module that needs a device. The Room Gradle Plugin puts the
    // exported schemas in this variant's assets, which is what
    // MigrationTestHelper opens the old database from.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}
