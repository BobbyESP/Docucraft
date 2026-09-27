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
     * Whether a tap at [event] is the handler's, such as a tap on a link. Asked when the finger
     * lifts from a tap. A claimed tap goes to [onTap] at once, without the wait for a second tap
     * that a double tap to zoom otherwise needs; elsewhere, double tap keeps working. Keep it
     * quick: it is asked on the main thread, in the middle of the gesture.
     */
    fun claimsTap(event: PdfTapEvent): Boolean = false

    /** A tap [claimsTap] claimed. The viewer's own `onTap` is not called for it. */
    fun onTap(event: PdfTapEvent) {}

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
