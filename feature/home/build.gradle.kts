/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.bobbyesp.docucraft.feature.home" }

dependencies {
    implementation(project(":scanner-api"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.landscapist.coil)
    implementation(libs.landscapist.placeholder)
    testImplementation(libs.mockk)
}
