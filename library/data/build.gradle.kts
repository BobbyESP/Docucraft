/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
    id("docucraft.android.room")
}

android {
    namespace = "com.bobbyesp.docucraft.library.data"

    // The test PDFs live with the engine's tests; the storage's and thumbnails' tests read them.
    sourceSets {
        getByName("androidTest") { assets.directories += "../../composepdf/src/androidTest/assets" }
    }
}

dependencies {
    api(project(":core:data"))
    api(project(":library:domain"))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.filekit)
    implementation(libs.bundles.koin)
    implementation(libs.landscapist.coil)
    implementation(libs.work.runtime)
    implementation(project(":composepdf"))
    implementation(project(":document-content-api"))
    implementation(project(":scanner-api"))
    testImplementation(project(":library:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.room.testing)
}
