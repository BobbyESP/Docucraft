/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id(libs.plugins.kotlin.jvm.get().pluginId) }

java {
    sourceCompatibility = ProjectConfig.javaVersion
    targetCompatibility = ProjectConfig.javaVersion
}

kotlin { compilerOptions { jvmTarget.set(ProjectConfig.jvmTarget) } }
