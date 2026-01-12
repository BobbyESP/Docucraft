import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.ktfmt.gradle)
    alias(libs.plugins.google.gms)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.stability.analyzer)
}

android {
    namespace = "com.bobbyesp.docucraft"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bobbyesp.docucraft"
        minSdk = 24
        targetSdk = 36

        versionCode = rootProject.extra["versionCode"] as Int
        versionName = rootProject.extra["versionName"] as String

        vectorDrawables { useSupportLibrary = true }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    applicationVariants.all {
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName =
                "Docucraft-${defaultConfig.versionName}-${name}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17) // Use the enum for target JVM version
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
    }

    composeCompiler { reportsDestination = layout.buildDirectory.dir("compose_compiler") }
}

ktfmt {
    // Google style - 2 space indentation & automatically adds/removes trailing commas
    // googleStyle()

    // KotlinLang style - 4 space indentation - From
    // https://kotlinlang.org/docs/coding-conventions.html
    kotlinLangStyle()
}

ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
    arg("KOIN_CONFIG_CHECK", "true")
}

dependencies {
    implementation(libs.bundles.core)
    implementation(libs.bundles.coroutines)
    implementation(libs.google.fonts)

    // Core UI libraries
    api(platform(libs.compose.bom))

    // Accompanist libraries
    implementation(libs.bundles.accompanist)

    // Compose libraries
    implementation(libs.bundles.compose)
    implementation(libs.materialKolor)
    implementation(libs.bundles.glance)
    implementation(libs.bundles.nav3)

    // Dependency injection
    implementation(libs.bundles.koin)

    // Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Key-value storage
    implementation(libs.datastore.preferences)

    // Image loading
    implementation(libs.landscapist.coil)

    // Files management
    implementation(libs.bundles.filekit)

    // Document scanner
    implementation(libs.gms.mlkit.docscanner)
    // implementation(libs.gms.mlkit.text.recognition)

    // Utilities
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sonner)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.profileinstaller)

    // Android testing libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
//    androidTestImplementation(libs.mockk.android)
//    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Compose testing and tooling libraries
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)

    debugImplementation(libs.leakcanary)
}

class RoomSchemaArgProvider(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) val schemaDir: File
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> {
        if (!schemaDir.exists()) {
            schemaDir.mkdirs()
        }
        return listOf("room.schemaLocation=${schemaDir.path}")
    }
}
