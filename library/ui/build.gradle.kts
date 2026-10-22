/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
    id("docucraft.android.compose")
}

android { namespace = "com.bobbyesp.docucraft.library.ui" }

dependencies {
    api(project(":core:domain"))
    api(project(":core:ui"))
    api(project(":core:designsystem"))
    api(project(":library:domain"))
    api(project(":core:navigation"))
    implementation(libs.androidx.annotation)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.haze)
    // `api`: its previews and components hand out immutable lists.
    api(libs.kotlinx.collections.immutable)
    implementation(libs.landscapist.coil)
    implementation(libs.landscapist.placeholder)
    implementation(libs.material.kolor)
    implementation(project(":scanner:api"))
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
