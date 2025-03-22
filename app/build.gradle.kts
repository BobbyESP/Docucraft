import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.ktfmt.gradle)
}

android {
    namespace = "com.bobbyesp.docucraft"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bobbyesp.docucraft"
        minSdk = 24
        targetSdk = 35

        versionCode = rootProject.extra["versionCode"] as Int
        versionName = rootProject.extra["versionName"] as String

        vectorDrawables {
            useSupportLibrary = true
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName = "Docucraft-${defaultConfig.versionName}-${name}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

ktfmt {
    // Google style - 2 space indentation & automatically adds/removes trailing commas
    //googleStyle()

    // KotlinLang style - 4 space indentation - From https://kotlinlang.org/docs/coding-conventions.html
    kotlinLangStyle()

}

ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
    arg("KOIN_CONFIG_CHECK", "true")
}

dependencies {
    implementation(libs.bundles.core)
    implementation(libs.bundles.coroutines)

    //Core UI libraries
    api(platform(libs.compose.bom))

    //Accompanist libraries
    implementation(libs.bundles.accompanist)

    //Compose libraries
    implementation(libs.bundles.compose)
    implementation(libs.materialKolor)

    //Pagination
    implementation(libs.bundles.pagination)

    //Network
    implementation(libs.bundles.ktor)

    //Dependency injection
    implementation(libs.bundles.koin)

    //Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    //Key-value storage
    implementation(libs.datastore.preferences)

    //Image loading
    implementation(libs.landscapist.coil)

    //Files management
    implementation(libs.bundles.filekit)

    //Document scanner
    implementation(libs.gms.mlkit.docscanner)

    //Utilities
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.scrollbar)
    implementation(libs.sonner)

    implementation(libs.profileinstaller)

    //Android testing libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)

    //Compose testing and tooling libraries
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