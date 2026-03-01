plugins {
    id(libs.plugins.android.library.get().pluginId)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.composepdf"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

}

dependencies {
    implementation(libs.bundles.core)
    implementation(libs.bundles.compose)

    api(platform(libs.compose.bom))

    implementation(libs.bundles.coroutines)
}