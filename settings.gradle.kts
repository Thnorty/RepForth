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
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:exercise-data")
include(":feature:exercises")
