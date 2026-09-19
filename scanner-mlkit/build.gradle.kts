/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id(libs.plugins.android.library.get().pluginId) }

android {
    namespace = "com.bobbyesp.scanner.mlkit"
    compileSdk = ProjectConfig.compileSdk

    defaultConfig { minSdk = ProjectConfig.minSdk }

    compileOptions {
        sourceCompatibility = ProjectConfig.javaVersion
        targetCompatibility = ProjectConfig.javaVersion
    }
}

dependencies {
    // `api`, because consumers speak the contract; everything below is `implementation`, so none
    // of it reaches them. That is what keeps ML Kit off :app's compile classpath.
    api(project(":scanner-api"))
    api(libs.androidx.activity)

    implementation(libs.gms.mlkit.docscanner)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.bundles.coroutines)

    testImplementation(libs.junit)
}
