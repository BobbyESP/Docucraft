/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("docucraft.android.feature") }

android { namespace = "com.bobbyesp.docucraft.feature.widgets" }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.landscapist.coil)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
