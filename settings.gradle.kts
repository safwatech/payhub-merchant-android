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
        // For local SDK development (testing an unreleased version of
        // payhub-android against this app), uncomment the line below and
        // first run `(cd ../../sdks/android && ./gradlew publishToMavenLocal)`.
        // Otherwise the published artefact from Maven Central is used.
        // mavenLocal()
    }
}

rootProject.name = "PayHubMerchant"
include(":app")
