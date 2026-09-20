/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins how the back stack turns into panes, which is the part of navigation that has no visible
 * symptom until someone opens the app on a tablet.
 *
 * `ListDetailSceneStrategy` is deliberately exercised directly rather than through a rendered
 * `NavDisplay`: the decision it makes is pure, so it can be asserted on a JVM without a device,
 * and every window size can be covered in milliseconds instead of one emulator per breakpoint.
 *
 * The rule being pinned, from the strategy's own source, is stricter than it looks: it reads the
 * pane metadata of the **last** entry, and gives up entirely — falling back to a single pane —
 * when that entry has none. Every destination that should keep a neighbour visible therefore has
 * to say so itself.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class ListDetailSceneSelectionTest {

    /**
     * A list on its own still claims a two-pane scene on a wide window: the second pane is filled
     * by the list's own `detailPlaceholder`. That placeholder is not decoration — it is the reason
     * Home does not stretch across a tablet with nothing beside it.
     */
    @Test
    fun `list alone still claims a scene on a wide window, filled by its placeholder`() {
        val scene = calculateScene(expanded, listOf(listEntry(Route.Home)))

        assertNotNull("The placeholder should hold the detail pane open", scene)
        assertEquals("Only Home is a real entry; the placeholder is not one", 1, scene?.entries?.size)
    }

    @Test
    fun `list alone falls back to a single pane on a narrow window`() {
        val scene = calculateScene(compact, listOf(listEntry(Route.Home)))

        assertNull("On a phone there is no room for a placeholder pane", scene)
    }

    @Test
    fun `list and detail share one scene on a wide window`() {
        val scene =
            calculateScene(expanded, listOf(listEntry(Route.Home), detailEntry(pdfViewer)))

        assertNotNull("Home and the open document should share the window", scene)
        assertEquals(2, scene?.entries?.size)
    }

    @Test
    fun `list and detail fall back to a single pane on a narrow window`() {
        val scene = calculateScene(compact, listOf(listEntry(Route.Home), detailEntry(pdfViewer)))

        assertNull("On a phone the strategy must defer to the single-pane one", scene)
    }

    /**
     * The gap this refactor is meant to close. Settings declares no pane role, so pushing it on
     * top of an open document makes the strategy give up on the whole scene — on any window size.
     */
    @Test
    fun `a destination without a pane role collapses the scene on a wide window`() {
        val scene =
            calculateScene(
                expanded,
                listOf(listEntry(Route.Home), detailEntry(pdfViewer), plainEntry(Route.Settings)),
            )

        assertNull("Settings has no pane role, so it takes the whole window", scene)
    }

    /**
     * Panes are grouped by `sceneKey`, which is what will let Settings become its own list-detail
     * scene later without disturbing the documents one.
     */
    @Test
    fun `panes belonging to different scene keys do not group together`() {
        val scene =
            calculateScene(
                expanded,
                listOf(
                    listEntry(Route.Home, sceneKey = "documents"),
                    detailEntry(pdfViewer, sceneKey = "settings"),
                ),
            )

        assertEquals(
            "Only the entries sharing the last entry's scene key belong to the scene",
            1,
            scene?.entries?.size,
        )
    }

    // ---------------- helpers ----------------

    private fun calculateScene(directive: PaneScaffoldDirective, entries: List<NavEntry<NavKey>>) =
        with(strategy(directive)) { with(SceneStrategyScope<NavKey>()) { calculateScene(entries) } }

    private fun strategy(directive: PaneScaffoldDirective) =
        ListDetailSceneStrategy<NavKey>(
            shouldHandleSinglePaneLayout = false,
            backNavigationBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
            directive = directive,
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
            paneExpansionDragHandle = null,
            paneExpansionState = null,
        )

    private fun listEntry(key: NavKey, sceneKey: Any = Unit) =
        NavEntry<NavKey>(
            key = key,
            metadata = ListDetailSceneStrategy.listPane(sceneKey = sceneKey) {},
        ) {}

    private fun detailEntry(key: NavKey, sceneKey: Any = Unit) =
        NavEntry<NavKey>(key = key, metadata = ListDetailSceneStrategy.detailPane(sceneKey)) {}

    private fun plainEntry(key: NavKey) = NavEntry<NavKey>(key = key) {}

    private companion object {
        val pdfViewer = Route.PdfViewer(documentUuid = "doc-1")

        /** One partition across: a phone, or a tablet held in portrait. */
        val compact = directive(maxHorizontalPartitions = 1)

        /** Two partitions across: a tablet in landscape, or an unfolded foldable. */
        val expanded = directive(maxHorizontalPartitions = 2)

        fun directive(maxHorizontalPartitions: Int) =
            PaneScaffoldDirective(
                maxHorizontalPartitions = maxHorizontalPartitions,
                horizontalPartitionSpacerSize = 0.dp,
                maxVerticalPartitions = 1,
                verticalPartitionSpacerSize = 0.dp,
                defaultPanePreferredWidth = 360.dp,
                excludedBounds = emptyList(),
            )
    }
}
