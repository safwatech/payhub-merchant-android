pluginManagement {
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
        // The PayHub Android SDK (ly.payhub:payhub-android:1.1.0) — until that
        // version is published to Maven Central by the safwatech/payhub-android
        // mirror, build it locally:  (cd ../../sdks/android && ./gradlew publishToMavenLocal)
        mavenLocal()
    }
}

rootProject.name = "PayHubMerchant"
include(":app")
