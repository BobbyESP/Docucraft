/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.application")
    id("docucraft.android.compose")
    alias(libs.plugins.google.gms)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.stability.analyzer)
    id("docucraft.copy-apk")
}

// Set by the release workflow, which decodes the keystore into the runner's temp directory.
// Without it a release build is left unsigned.
val signingStorePath: String? = System.getenv("SIGNING_KEY_STORE_PATH")

android {
    namespace = "com.bobbyesp.docucraft"

    defaultConfig {
        applicationId = "com.bobbyesp.docucraft"

        versionCode = rootProject.extra["versionCode"] as Int
        versionName = rootProject.extra["versionName"] as String
    }

    signingConfigs {
        if (signingStorePath != null) {
            create("release") {
                storeFile = file(signingStorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (signingStorePath != null) signingConfig = signingConfigs.getByName("release")
            ndk { debugSymbolLevel = "FULL" }
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources { generateLocaleConfig = true }

    // The test PDFs live with the engine's tests; the viewer's content tests read the same ones.
    sourceSets.getByName("androidTest").assets.directories += "../composepdf/src/androidTest/assets"
}

composeCompiler { reportsDestination = layout.buildDirectory.dir("compose_compiler") }

dependencies {
    // The features and what they are built on. The app is their shell: it starts them, draws the
    // one back stack they share, and chooses the engines behind their ports.
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:organization"))
    implementation(project(":feature:bin"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:document-actions"))
    implementation(project(":feature:pdfviewer"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:widgets"))
    implementation(project(":library:domain"))
    implementation(project(":library:data"))
    implementation(project(":library:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))

    // The engines, named only by the Koin modules that bind them (DocumentScannerModule,
    // PageContentModule), so no ML Kit or platform content type is on any feature's classpath.
    implementation(project(":scanner-api"))
    implementation(project(":scanner-mlkit"))
    implementation(project(":document-content-api"))
    implementation(project(":document-content-platform"))
    implementation(project(":ocr-mlkit"))

    // AndroidX: the activities, the splash screen, and AppCompat's locale service in the manifest.
    implementation(libs.bundles.androidx.core)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.navigation3)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.filekit)
    implementation(libs.landscapist.coil)
    implementation(libs.sonner)
    implementation(libs.datastore.preferences)
    // WorkManager starts from App's configuration, not from its initializer.
    implementation(libs.work.runtime)

    // Firebase: the app applies its Gradle plugins.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.leakcanary)

    // Testing & Tooling
    testImplementation(project(":library:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.mockk)
    androidTestImplementation(project(":composepdf"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    // The device tests build the catalogue themselves (DocumentsDatabase.builder).
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
