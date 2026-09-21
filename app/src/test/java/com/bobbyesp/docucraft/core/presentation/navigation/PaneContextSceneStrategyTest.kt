/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.bobbyesp.docucraft.core.presentation.navigation.pane.PaneAwareScene
import com.bobbyesp.docucraft.core.presentation.navigation.pane.PaneContext
import com.bobbyesp.docucraft.core.presentation.navigation.pane.sharingTheWindow
import com.bobbyesp.docucraft.feature.docscanner.navigation.Home
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a scene tells the destinations inside it, and what wrapping a scene must not cost.
 *
 * The answer comes from the strategy rather than from counting a scene's entries, which was a guess
 * and a wrong one on every tablet: a list alone on a wide window is *one* entry inside a *two*-pane
 * scaffold, because the second pane holds the list's `detailPlaceholder` and a placeholder is not
 * an entry. The two library scene types that could have answered honestly are both `internal`, so
 * the question has to be put to whoever made the layout decision.
 */
class PaneContextSceneStrategyTest {

    @Test
    fun `a strategy that shares the window says so to everything it lays out`() {
        val scene = calculateScene(sharing(FakeScene(key = "pair", entries = listOf(entry(Home)))))

        val paneContext = (scene as PaneAwareScene).paneContextForTest

        assertFalse(paneContext.isSolePane)
        assertFalse(
            "Beside the list there is already a way back on screen",
            paneContext.providesOwnBackAffordance,
        )
    }

    /**
     * The case that used to be got wrong. A scene holding one entry is not evidence of a sole pane:
     * what makes it one is that no layout strategy claimed the stack at all.
     */
    @Test
    fun `one entry in a shared scene is still not a sole pane`() {
        val single = FakeScene(key = "list-only", entries = listOf(entry(Home)))

        assertFalse(
            (calculateScene(sharing(single)) as PaneAwareScene).paneContextForTest.isSolePane
        )
    }

    /**
     * `ListDetailSceneStrategy` returns null unless two panes really fit, and that null has to
     * survive the wrapper: it is what lets the stack fall through to the single-pane fallback,
     * where [PaneContext]'s own default is already the right answer.
     */
    @Test
    fun `a strategy that declines still declines once wrapped`() {
        assertNull(calculateScene(sharing(scene = null)))
    }

    @Test
    fun `a destination outside any shared scene fills the window`() {
        val default = PaneContext(isSolePane = true)

        assertTrue(default.isSolePane)
        assertTrue(default.providesOwnBackAffordance)
    }

    /**
     * `NavDisplay` identifies a scene by `(its class, its key)`. Wrapping every scene in one class
     * would make them all share it, so two scenes that differ only by type — a single pane and a
     * list-detail pair, the most important transition in the app — would stop animating between
     * each other. The wrapped class has to survive into the key.
     */
    @Test
    fun `wrapping keeps scenes of different types distinguishable`() {
        val one = PaneAwareScene(FakeScene(key = Unit, entries = listOf(entry(Home))), shared)
        val other =
            PaneAwareScene(OtherFakeScene(key = Unit, entries = listOf(entry(Home))), shared)

        assertNotEquals(
            "Two scene types sharing a key must not collapse into one identity",
            one.key,
            other.key,
        )
    }

    @Test
    fun `wrapping keeps the same scene equal to itself`() {
        val entries = listOf(entry(Home), entry(document))

        assertEquals(
            PaneAwareScene(FakeScene(key = "pair", entries = entries), shared),
            PaneAwareScene(FakeScene(key = "pair", entries = entries), shared),
        )
    }

    // ---------------- helpers ----------------

    private fun calculateScene(strategy: SceneStrategy<NavKey>) =
        with(strategy) {
            with(SceneStrategyScope<NavKey>()) { calculateScene(listOf(entry(Home))) }
        }

    /** A strategy that hands back [scene], wrapped the way the shell wraps the real one. */
    private fun sharing(scene: Scene<NavKey>?): SceneStrategy<NavKey> =
        SceneStrategy<NavKey> { scene }.sharingTheWindow()

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
        val shared = PaneContext(isSolePane = false)
    }
}
