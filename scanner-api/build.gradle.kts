/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id(libs.plugins.kotlin.jvm.get().pluginId) }

// Deliberately a plain Kotlin module, with no Android plugin and no dependencies. That is the
// enforcement: a scanner engine type, an android.* import or a Compose annotation cannot be added
// here without the build failing, so the contract stays something any engine could satisfy.

java {
    sourceCompatibility = ProjectConfig.javaVersion
    targetCompatibility = ProjectConfig.javaVersion
}

kotlin { compilerOptions { jvmTarget.set(ProjectConfig.jvmTarget) } }
