/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
    id("docucraft.android.compose")
}

android {
    namespace = "com.composepdf"

    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
}

dependencies {
    implementation(libs.bundles.androidx.core)
    implementation(libs.bundles.compose)

    api(platform(libs.androidx.compose.bom))

    implementation(libs.bundles.coroutines)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
