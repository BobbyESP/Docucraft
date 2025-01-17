import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

apply(plugin = libs.plugins.google.gms.get().pluginId)
apply(plugin = "com.google.firebase.crashlytics")

val commitSignature = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().substringBefore("\n")

val currentVersion: Version = Version.Beta(
    versionMajor = 1,
    versionMinor = 0,
    versionPatch = 0,
    versionBuild = 2
)

android {
    namespace = "com.bobbyesp.docucraft"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bobbyesp.docucraft"
        minSdk = 24
        targetSdk = 34

        versionCode = currentVersion.toVersionCode()
        versionName = currentVersion.toVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            extensions.configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = true
                nativeSymbolUploadEnabled = false
            }

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Docucraft-${defaultConfig.versionName}-${name}.apk"
        }
    }

    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).all {
        kotlinOptions.freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}

ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
}

dependencies {
    implementation(project(":color"))
    implementation(project(":app:utilities"))
    implementation(project(":app:ui"))
//---------------Core----------------//
    implementation(libs.bundles.core) //⚠️ This contains core kotlinx libraries, lifecycle runtime and Activity Compose support

//---------------User Interface---------------//
//Core UI libraries
    api(platform(libs.compose.bom.canary))

//Accompanist libraries
    implementation(libs.bundles.accompanist)

//Compose libraries
    implementation(libs.bundles.compose)
    implementation(libs.material)

//Pagination
    implementation(libs.bundles.pagination)

//---------------Dependency Injection---------------//
    implementation(libs.bundles.hilt)
    ksp(libs.hilt.ext.compiler)
    ksp(libs.hilt.compiler)

//-------------------Database-------------------//
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

//-------------------Key-value Storage-------------------//
    implementation(libs.mmkv)

//-------------------Image Loading-------------------//
    implementation(libs.landscapist.coil)
    implementation(libs.coil)
    implementation(libs.compose.coil)

//-------------------FIREBASE-------------------//
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

//-------------------Utilities-------------------//
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.qrcode.kotlin.android)
    implementation(libs.profileinstaller)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.scrollbar)
    implementation(libs.gms.mlkit.docscanner)

//-------------------Testing-------------------//
//Android testing libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)

//Compose testing and tooling libraries
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test.junit4)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
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

sealed class Version(
    open val versionMajor: Int,
    val versionMinor: Int,
    val versionPatch: Int,
    val versionBuild: Int = 0,
    val commitId: String = ""
) {
    abstract fun toVersionName(): String

    fun toVersionCode(): Int {
        val minorExtraDigit = if (versionMinor > 9) {
            (versionMinor / 10).toString()
        } else {
            ""
        }

        return "$versionMajor$minorExtraDigit$versionPatch$versionBuild".toInt()
    }

    class Stable(versionMajor: Int, versionMinor: Int, versionPatch: Int) :
        Version(versionMajor, versionMinor, versionPatch) {
        override fun toVersionName(): String = "${versionMajor}.${versionMinor}.${versionPatch}"
    }

    class ReleaseCandidate(
        versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int
    ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName(): String =
            "${versionMajor}.${versionMinor}.${versionPatch}-rc.$versionBuild"
    }

    class Beta(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
        Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName(): String =
            "${versionMajor}.${versionMinor}.${versionPatch}-beta.$versionBuild"
    }

    class Alpha(
        versionMajor: Int, versionMinor: Int, versionPatch: Int, commitId: String
    ) : Version(versionMajor, versionMinor, versionPatch, commitId = commitId) {
        override fun toVersionName(): String =
            "${versionMajor}.${versionMinor}.${versionPatch}-alpha.$commitId"
    }
}