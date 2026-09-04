plugins {
    `kotlin-dsl`
}

group = "com.repforth.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.baselineprofile.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "repforth.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "repforth.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "repforth.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "repforth.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "repforth.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidInstrumentation") {
            id = "repforth.android.instrumentation"
            implementationClass = "AndroidInstrumentationConventionPlugin"
        }
        register("androidScreenshot") {
            id = "repforth.android.screenshot"
            implementationClass = "AndroidScreenshotConventionPlugin"
        }
        register("wearApplication") {
            id = "repforth.wear.application"
            implementationClass = "WearApplicationConventionPlugin"
        }
        register("baselineProfileConsumer") {
            id = "repforth.baselineprofile.consumer"
            implementationClass = "BaselineProfileConsumerConventionPlugin"
        }
        register("baselineProfileProducer") {
            id = "repforth.baselineprofile.producer"
            implementationClass = "BaselineProfileProducerConventionPlugin"
        }
    }
}
