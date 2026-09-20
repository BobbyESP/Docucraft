/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategyScope
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlayPreference
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.docucraft.feature.docscanner.navigation.DeleteDocument
import com.bobbyesp.docucraft.feature.docscanner.navigation.DocumentActions
import com.bobbyesp.docucraft.feature.docscanner.navigation.Home
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which container an overlay destination gets, and what it leaves standing underneath.
 *
 * The choice used to be an `if` on the window size inside the screen that opened the sheet. As a
 * property of the strategy it can be asserted for both window sizes on a JVM, rather than needing
 * one emulator per breakpoint.
 *
 * The scene types are private to the strategy, so what is asserted is the identity it hands to
 * `NavDisplay`: the same destination rendered two ways must be two scenes, and rendered the same
 * way twice must be one.
 */
class OverlaySceneSelectionTest {

    @Test
    fun `an adaptive overlay is rendered differently on a narrow and a wide window`() {
        val narrow = sceneFor(deleteOverlay, windowIsWide = false)
        val wide = sceneFor(deleteOverlay, windowIsWide = true)

        assertNotNull(narrow)
        assertNotNull(wide)
        assertNotEquals(
            "A sheet and a dialog of one destination must not share an identity",
            narrow?.key,
            wide?.key,
        )
    }

    /** The actions grid is a list of choices; a dialog would only make it smaller. */
    @Test
    fun `an overlay that always wants a sheet is rendered the same on any window`() {
        val narrow = sceneFor(actionsOverlay, windowIsWide = false)
        val wide = sceneFor(actionsOverlay, windowIsWide = true)

        assertNotNull(narrow)
        assertEquals("Asking for a sheet should not change with the window", narrow?.key, wide?.key)
    }

    @Test
    fun `an overlay renders only itself`() {
        val scene = sceneFor(deleteOverlay, windowIsWide = false)

        assertTrue(scene is OverlayScene)
        assertEquals(
            listOf(deleteOverlay.contentKey),
            scene?.entries?.map { it.contentKey },
        )
    }

    @Test
    fun `whatever is underneath stays on screen below the overlay`() {
        val scene = sceneFor(deleteOverlay, windowIsWide = false)

        assertEquals(
            listOf(Home.toString()),
            (scene as? OverlayScene)?.overlaidEntries?.map { it.contentKey },
        )
    }

    @Test
    fun `a destination with no overlay metadata is left to the layout strategies`() {
        assertNull(calculateScene(windowIsWide = false, entries = listOf(plainEntry(Home))))
    }

    /**
     * An overlay has to sit above something. Claiming a stack whose only entry is the overlay would
     * leave `overlaidEntries` empty, which is not allowed, so it declines instead.
     */
    @Test
    fun `an overlay alone on the stack is declined rather than left with nothing beneath it`() {
        assertNull(calculateScene(windowIsWide = false, entries = listOf(deleteOverlay)))
    }

    // ---------------- helpers ----------------

    private fun sceneFor(overlay: NavEntry<NavKey>, windowIsWide: Boolean): Scene<NavKey>? =
        calculateScene(windowIsWide, listOf(plainEntry(Home), overlay))

    private fun calculateScene(windowIsWide: Boolean, entries: List<NavEntry<NavKey>>) =
        with(OverlaySceneStrategy<NavKey>(windowIsWide)) {
            with(SceneStrategyScope<NavKey>()) { calculateScene(entries) }
        }

    private fun plainEntry(key: NavKey) = NavEntry<NavKey>(key = key) {}

    private companion object {
        val deleteOverlay =
            NavEntry<NavKey>(
                key = DeleteDocument(documentUuid = "doc-1"),
                metadata = OverlaySceneStrategy.overlay(),
            ) {}

        val actionsOverlay =
            NavEntry<NavKey>(
                key = DocumentActions(documentUuid = "doc-1"),
                metadata = OverlaySceneStrategy.overlay(OverlayPreference.AlwaysSheet),
            ) {}
    }
}
