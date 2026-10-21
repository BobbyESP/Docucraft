/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
    id("docucraft.android.compose")
}

android { namespace = "com.bobbyesp.docucraft.feature.settings" }

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":library:domain"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.haze)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.navigation3)
    implementation(libs.kotlinx.collections.immutable)
    testImplementation(libs.junit)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
