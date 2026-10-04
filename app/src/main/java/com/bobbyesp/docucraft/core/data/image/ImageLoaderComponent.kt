/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.data.image

import coil.ComponentRegistry

/**
 * What a feature adds to the app's image loader: how to load a model of its own.
 *
 * The loader is built in `core`, which knows no feature. A feature binds one of these in its own
 * module, under a name of its own, and the loader collects them all.
 */
fun interface ImageLoaderComponent {
    fun register(registry: ComponentRegistry.Builder)
}
