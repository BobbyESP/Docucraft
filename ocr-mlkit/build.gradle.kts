/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id(libs.plugins.android.library.get().pluginId) }

android {
    namespace = "com.bobbyesp.ocr.mlkit"
    compileSdk = ProjectConfig.compileSdk
    // The PDF engine this draws pages with is built against the minor release.
    compileSdkMinor = ProjectConfig.compileSdkMinor

    defaultConfig {
        minSdk = ProjectConfig.minSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = ProjectConfig.javaVersion
        targetCompatibility = ProjectConfig.javaVersion
        // The PDF engine asks it of whatever depends on it.
        isCoreLibraryDesugaringEnabled = true
    }

    // The same test PDFs as the engine and the app, described by the same manifest.
    sourceSets {
        getByName("androidTest") { assets.srcDir("../composepdf/src/androidTest/assets") }
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

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
