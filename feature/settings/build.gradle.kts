/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("docucraft.android.feature") }

android { namespace = "com.bobbyesp.docucraft.feature.settings" }

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.collections.immutable)
}
