pluginManagement {
    repositories {
        // ✅ Google's plugin portal (includes Android Gradle Plugin & Firebase)
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()        // ✅ Required for AndroidX and Firebase libraries
        mavenCentral()  // ✅ Additional open-source libraries
    }
}

rootProject.name = "AcadLink"
include(":app")
