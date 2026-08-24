/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * The pane directive the app's list-detail scene is actually using.
 *
 * Provided once by [DocucraftApp] and handed to the scene strategy, so a screen asking "am I
 * sharing the window with another pane?" and the strategy deciding how many panes to lay out are
 * answering from the same value. Deriving the answer separately from a raw width breakpoint is what
 * produced a full-screen PDF viewer with no back button on 600–839dp windows: two panes need
 * *expanded* width (840dp+), not merely *medium*.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val LocalPaneScaffoldDirective =
    compositionLocalOf<PaneScaffoldDirective> { error("No PaneScaffoldDirective provided") }

/**
 * True when the list-detail scene is actually showing two panes side by side.
 *
 * Prefer this over a window-width check whenever the question is "is another pane on screen with
 * me?", because that is a layout outcome, not a width.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val isMultiPaneLayout: Boolean
    @Composable get() = LocalPaneScaffoldDirective.current.maxHorizontalPartitions > 1
