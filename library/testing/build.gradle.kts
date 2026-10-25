/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("docucraft.jvm.library")
}

dependencies {
    api(project(":core:domain"))
    api(project(":library:domain"))
    api(libs.kotlinx.coroutines.core)
    api(libs.mockk)
    api(project(":content:api"))
    api(project(":scanner:api"))
}
