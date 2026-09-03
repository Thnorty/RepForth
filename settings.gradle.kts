pluginManagement {
    // Convention plugins live in a composite build so module files stay declarative.
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RepForth"

// Modules are added as they earn their keep (§5: no empty modules for symmetry).
include(":app")
include(":baselineprofile")
include(":core:ai")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:exercise-data")
include(":core:media")
include(":core:rules")
include(":core:testing")
include(":core:transfer")
include(":core:user-data")
include(":core:secrets")
include(":core:workout")
include(":feature:builder")
include(":feature:exercises")
include(":feature:history")
include(":feature:home")
include(":feature:onboarding")
include(":feature:session")
include(":feature:settings")
