/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
    id("docucraft.android.compose")
}

android { namespace = "com.bobbyesp.docucraft.core.designsystem" }

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.annotation)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.haze)
    implementation(libs.google.fonts)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.landscapist.coil)
    implementation(libs.landscapist.placeholder)
    implementation(libs.material.kolor)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
