/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
    id("docucraft.android.compose")
}

android { namespace = "com.bobbyesp.docucraft.feature.documentactions" }

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:document-actions:api"))
    implementation(project(":library:domain"))
    implementation(project(":library:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:organization:api"))
    implementation(project(":feature:scan:api"))
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.koin)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
