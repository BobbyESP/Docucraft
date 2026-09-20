/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import com.bobbyesp.docucraft.core.presentation.navigation.pane.PaneAwareScene
import com.bobbyesp.docucraft.feature.docscanner.navigation.Home
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the scene tells the destinations inside it, and what wrapping a scene must not cost. */
class PaneContextSceneDecoratorTest {

    @Test
    fun `a scene rendering one entry reports that entry as sole`() {
        val scene = PaneAwareScene(FakeScene(key = "single", entries = listOf(entry(Home))))

        assertTrue(scene.paneContextForTest.isSolePane)
        assertTrue(scene.paneContextForTest.providesOwnBackAffordance)
    }

    @Test
    fun `a scene rendering two entries reports neither as sole`() {
        val scene =
            PaneAwareScene(FakeScene(key = "pair", entries = listOf(entry(Home), entry(document))))

        assertFalse(scene.paneContextForTest.isSolePane)
        assertFalse(
            "Beside the list there is already a way back on screen",
            scene.paneContextForTest.providesOwnBackAffordance,
        )
    }

    /**
     * `NavDisplay` identifies a scene by `(its class, its key)`. Wrapping every scene in one class
     * would make them all share it, so two scenes that differ only by type — a single pane and a
     * list-detail pair, the most important transition in the app — would stop animating between
     * each other. The wrapped class has to survive into the key.
     */
    @Test
    fun `wrapping keeps scenes of different types distinguishable`() {
        val single = PaneAwareScene(FakeScene(key = Unit, entries = listOf(entry(Home))))
        val pair = PaneAwareScene(OtherFakeScene(key = Unit, entries = listOf(entry(Home))))

        assertNotEquals(
            "Two scene types sharing a key must not collapse into one identity",
            single.key,
            pair.key,
        )
    }

    @Test
    fun `wrapping keeps the same scene equal to itself`() {
        val entries = listOf(entry(Home), entry(document))

        assertEquals(
            PaneAwareScene(FakeScene(key = "pair", entries = entries)),
            PaneAwareScene(FakeScene(key = "pair", entries = entries)),
        )
    }

    // ---------------- fakes ----------------

    private fun entry(key: NavKey) = NavEntry<NavKey>(key = key) {}

    private data class FakeScene(
        override val key: Any,
        override val entries: List<NavEntry<NavKey>>,
    ) : Scene<NavKey> {
        override val previousEntries: List<NavEntry<NavKey>> = entries.dropLast(1)
        override val content: @Composable () -> Unit = {}
    }

    /** A second scene type, to stand in for "single pane" versus "list detail". */
    private data class OtherFakeScene(
        override val key: Any,
        override val entries: List<NavEntry<NavKey>>,
    ) : Scene<NavKey> {
        override val previousEntries: List<NavEntry<NavKey>> = entries.dropLast(1)
        override val content: @Composable () -> Unit = {}
    }

    private companion object {
        val document = PdfViewer(documentUuid = "doc-1")
    }
}
