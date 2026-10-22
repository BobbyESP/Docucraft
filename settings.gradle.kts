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

// Core: what every module builds on
include(":core:domain")

include(":core:data")

include(":core:designsystem")

include(":core:navigation")

include(":core:ui")

// The library: the catalogue several features share
include(":library:domain")

include(":library:data")

include(":library:ui")

include(":library:testing")

// Engines: a contract (api) and its implementations, and the PDF engine
include(":scanner:api")

include(":scanner:mlkit")

include(":content:api")

include(":content:platform")

include(":content:mlkit")

include(":composepdf")

// Features: what the user sees
include(":feature:home")

include(":feature:search")

include(":feature:organization")

include(":feature:bin")

include(":feature:scan")

include(":feature:document-actions")

include(":feature:pdfviewer")

include(":feature:settings")

include(":feature:widgets")
