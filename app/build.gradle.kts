/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id(libs.plugins.android.application.get().pluginId)
    id("docucraft.android.convention")
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.google.gms)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.stability.analyzer)
    id("copy-apk-plugin")
}

// Set by the release workflow, which decodes the keystore into the runner's temp directory.
// Without it a release build is left unsigned.
val signingStorePath: String? = System.getenv("SIGNING_KEY_STORE_PATH")

android {
    namespace = "com.bobbyesp.docucraft"

    defaultConfig {
        applicationId = "com.bobbyesp.docucraft"
        targetSdk = ProjectConfig.targetSdk

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

    // The exported schemas, which the migration tests build each old version of the database from.
    sourceSets.getByName("androidTest").assets.directories += "schemas"
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(
        project.layout.projectDirectory.file("compose_stability_main.conf")
    )
}

ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
    arg("KOIN_CONFIG_CHECK", "true")
}

dependencies {
    // Bundles
    implementation(libs.bundles.androidx.core)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.navigation3)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.glance)
    implementation(libs.bundles.filekit)
    implementation(libs.bundles.accompanist)

    // Platforms
    api(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    // UI & Misc
    implementation(libs.google.fonts)
    implementation(libs.material.kolor)
    implementation(libs.landscapist.coil)
    implementation(libs.landscapist.placeholder)
    implementation(libs.sonner)
    implementation(libs.bundles.haze)

    // Storage
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // Background work: reading the text of documents
    implementation(libs.work.runtime)

    // Scanning. The engine lives behind :scanner-api and is only named by the Koin module, so
    // no ML Kit type is on this module's compile classpath at all.
    implementation(project(":scanner-api"))
    implementation(project(":scanner-mlkit"))

    // What is on a document's pages, behind a contract text recognition can implement too.
    implementation(project(":document-content-api"))
    implementation(project(":ocr-mlkit"))

    // Links from documents open in a Custom Tab, which stays in the app's task (D4).
    implementation(libs.androidx.browser)

    // KotlinX
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    // Firebase
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Performance & Utils
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.leakcanary)

    // Local Projects
    implementation(project(":composepdf"))

    // Testing & Tooling
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

class RoomSchemaArgProvider(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) val schemaDir: File
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        if (!schemaDir.exists()) schemaDir.mkdirs()
        return listOf("room.schemaLocation=${schemaDir.path}")
    }
}
