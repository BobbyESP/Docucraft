/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
}

android { namespace = "com.bobbyesp.docucraft.core.data" }

dependencies {
    api(project(":core:domain"))
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.koin)
    implementation(libs.datastore.preferences)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.landscapist.coil)
    implementation(platform(libs.firebase.bom))
}
