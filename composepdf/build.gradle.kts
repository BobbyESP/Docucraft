plugins {
    id("android.feature.convention")
}

android {
    namespace = "com.composepdf"
}

dependencies {
    implementation(libs.bundles.androidx.core)
    implementation(libs.bundles.compose)

    api(platform(libs.androidx.compose.bom))

    implementation(libs.bundles.coroutines)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
