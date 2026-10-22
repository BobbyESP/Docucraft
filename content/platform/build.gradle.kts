/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.android.library")
}

android { namespace = "com.bobbyesp.documentcontent.platform" }

dependencies {
    // `api`, because consumers speak the contract. The platform's own content types stay here, the
    // only module that uses them.
    api(project(":content:api"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.coroutines)
    implementation(project(":composepdf"))
    testImplementation(libs.junit)
}
