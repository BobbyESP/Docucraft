plugins {
    id(libs.plugins.android.application.get().pluginId)
    id("docucraft.android.convention")
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.ktfmt.gradle)
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
    implementation(libs.bundles.core)
    implementation(libs.bundles.coroutines)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.google.fonts)

    api(platform(libs.compose.bom))
    implementation(libs.androidx.fragment.compose)

    implementation(libs.bundles.accompanist)
    implementation(libs.bundles.compose)
    implementation(libs.materialKolor)
    implementation(libs.bundles.glance)
    implementation(libs.bundles.nav3)

    implementation(project(":composepdf"))

    implementation(libs.bundles.koin)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.landscapist.coil)
    implementation(libs.bundles.filekit)

    implementation(libs.gms.mlkit.docscanner)

    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sonner)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.profileinstaller)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    debugImplementation(libs.leakcanary)
}

class RoomSchemaArgProvider(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) val schemaDir: File
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        if (!schemaDir.exists()) schemaDir.mkdirs()
        return listOf("room.schemaLocation=${schemaDir.path}")
    }
}
