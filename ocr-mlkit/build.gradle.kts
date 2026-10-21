/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("docucraft.android.library") }

android {
    namespace = "com.bobbyesp.ocr.mlkit"

    // The same test PDFs as the engine and the app, described by the same manifest.
    sourceSets {
        getByName("androidTest") { assets.directories += "../composepdf/src/androidTest/assets" }
    }
}

dependencies {
    // `api`, because consumers speak the contract; everything below is `implementation`, so none
    // of it reaches them. That is what keeps ML Kit off :app's compile classpath.
    api(project(":document-content-api"))

    // For `PdfRenderers`: a page is drawn to be read, and a renderer is never built by hand.
    implementation(project(":composepdf"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.gms.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.bundles.coroutines)

    androidTestImplementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
