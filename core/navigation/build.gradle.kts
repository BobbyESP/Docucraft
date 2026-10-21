/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
    id("docucraft.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.bobbyesp.docucraft.core.navigation" }

dependencies {
    api(project(":core:designsystem"))
    // `api`: every key is a NavKey.
    api(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.navigation3)
    testImplementation(libs.junit)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
