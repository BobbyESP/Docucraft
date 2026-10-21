/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
    id("docucraft.android.compose")
}

android { namespace = "com.bobbyesp.docucraft.feature.widgets" }

dependencies {
    // Its strings: the widgets show some of the app's own words.
    implementation(project(":core:designsystem"))
    implementation(project(":library:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.koin)
    implementation(libs.landscapist.coil)
    testImplementation(project(":library:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
