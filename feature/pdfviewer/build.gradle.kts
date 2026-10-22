/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.bobbyesp.docucraft.feature.pdfviewer" }

dependencies {
    implementation(project(":composepdf"))
    implementation(project(":document-content-api"))
    implementation(project(":scanner-api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.landscapist.coil)
    implementation(libs.sonner)
    testImplementation(libs.mockk)
}
