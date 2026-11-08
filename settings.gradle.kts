pluginManagement {
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

rootProject.name = "Cadence"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":core:testing")
include(":feature:onboarding")
include(":feature:today")
include(":feature:calendar")
include(":feature:insights")
include(":feature:settings")
