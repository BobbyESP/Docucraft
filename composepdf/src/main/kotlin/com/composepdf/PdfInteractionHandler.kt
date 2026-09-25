/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

/**
 * Lets the caller take over touches the viewer would otherwise use to move the document, such as a
 * long press that starts a text selection and a drag that extends it.
 *
 * The viewer stays generic: it does not know what a claimed gesture is for. Every callback runs on
 * the main thread, inside the viewer's touch handling, so keep them short.
 */
interface PdfInteractionHandler {
    /**
     * A long press. Return `true` to claim the gesture: until the finger lifts, its movement goes
     * to [onDrag] instead of moving the document, and neither pinch nor fling happen. Return
     * `false` to leave it to the viewer, which then calls its own `onLongPress`.
     */
    fun onLongPress(event: PdfTapEvent): Boolean = false

    /** The finger of a claimed gesture moved. */
    fun onDrag(event: PdfTapEvent) {}

    /** A claimed gesture ended: the finger lifted, or the gesture was cancelled. Always called. */
    fun onDragEnd() {}
}
