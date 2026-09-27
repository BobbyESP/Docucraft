/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.domain.model

/**
 * How the PDF viewer fits pages to the window. The app's own type rather than the rendering
 * engine's, so a stored preference does not change format if the engine does; the viewer maps it at
 * the edge.
 *
 * The names match the engine's on purpose: they are also the values analytics has always received.
 */
enum class ViewerFitMode {
    /** Each page fills the window's width: the usual reading mode. */
    WIDTH,

    /** Each page fills the window's height. */
    HEIGHT,

    /** Each page fits entirely in the window. */
    BOTH,

    /** Pages keep their true relative sizes, scaled to the widest one. */
    PROPORTIONAL,
}
