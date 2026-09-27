/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.gesture

/** Past any device's double-tap timeout: a single tap is only a tap once this has passed. */
internal const val DOUBLE_TAP_WINDOW_MS = 1_000L

/** Past any device's long-press timeout. */
internal const val LONG_PRESS_MS = 1_000L

/** Long enough for a zoom animation to finish. */
internal const val SETTLE_MS = 2_000L
