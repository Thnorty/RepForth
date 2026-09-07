plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.repforth.android.instrumentation)
}

android {
    namespace = "com.repforth.core.userdata"
}

dependencies {
    // The one door to user data, mirroring core:exercise-data for the catalog.
    // Features depend on this; nothing outside it sees a DAO.
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    api(project(":core:workout"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Session recovery is a statement about what survives a real database, not
    // about what a fake row store hands back. A fake DAO holds the entity object
    // it was given, so a field the repository never writes to a column still
    // comes back — which is exactly the shape of the two defects these tests
    // exist for. Room in memory is still real Room: real DDL, real columns, the
    // real @Relation queries.
    androidTestImplementation(project(":core:common"))
    androidTestImplementation(project(":core:database"))
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
