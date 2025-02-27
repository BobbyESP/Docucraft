plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.bobbyesp.docucraft"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bobbyesp.docucraft"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    annotationProcessor(libs.room.compiler)

    //Key-value storage
    implementation(libs.datastore.preferences)

    //Image loading
    implementation(libs.landscapist.coil)

}