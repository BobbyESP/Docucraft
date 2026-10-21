/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
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
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Docucraft"

include(":app")

include(":composepdf")

include(":scanner-api")

include(":scanner-mlkit")

include(":document-content-api")

include(":ocr-mlkit")

include(":core:domain")

include(":core:data")

include(":core:designsystem")

include(":core:navigation")

include(":core:ui")

include(":library:domain")

include(":library:testing")

include(":library:data")

include(":library:ui")

include(":document-content-platform")

include(":feature:bin:api")

include(":feature:document-actions:api")

include(":feature:search:api")

include(":feature:organization:api")

include(":feature:settings:api")

include(":feature:viewer:api")

include(":feature:scan:api")

include(":feature:settings")

include(":feature:bin")

include(":feature:search")

include(":feature:organization")

include(":feature:scan")

include(":feature:document-actions")

include(":feature:viewer")
