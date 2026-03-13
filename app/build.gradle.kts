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

android {
    namespace = "com.bobbyesp.docucraft"

    defaultConfig {
        applicationId = "com.bobbyesp.docucraft"
        targetSdk = ProjectConfig.targetSdk

        versionCode = rootProject.extra["versionCode"] as Int
        versionName = rootProject.extra["versionName"] as String
    }

    buildTypes {
        release {
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

    androidResources {
        generateLocaleConfig = true
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(
        project.layout.projectDirectory.file("compose_stability_main.conf"),
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
    implementation(libs.sonner)
    
    // Storage
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // ML Kit
    implementation(libs.gms.mlkit.docscanner)

    //KotlinX
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
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)

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
