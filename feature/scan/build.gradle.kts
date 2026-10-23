/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("docucraft.android.feature") }

android { namespace = "com.bobbyesp.docucraft.feature.scan" }

dependencies { testImplementation(project(":content:api")) }
