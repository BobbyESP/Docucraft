/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.bobbyesp.docucraft.feature.organization.api" }

dependencies {
    // `api`: every key is a NavKey.
    api(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
}
