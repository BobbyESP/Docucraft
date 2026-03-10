plugins {
    id(libs.plugins.android.library.get().pluginId)
    id("docucraft.android.convention")
}

android {
    namespace = "com.composepdf"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(libs.bundles.core)
    implementation(libs.bundles.compose)

    api(platform(libs.compose.bom))

    implementation(libs.bundles.coroutines)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
