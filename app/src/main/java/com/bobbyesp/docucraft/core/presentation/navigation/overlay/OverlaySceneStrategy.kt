/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation.overlay

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass

/** How an overlay destination is being presented. */
enum class OverlayPresentation {
    /** A modal bottom sheet, reachable with a thumb. */
    Sheet,

    /** A centred dialog, which is what a wide window has the room for. */
    Dialog,
}

/** What a destination asks for, which is not always what the window can give it. */
enum class OverlayPreference {
    /** A sheet whatever the window, for content that is a list of choices. */
    AlwaysSheet,

    /** A sheet where the window is narrow, a dialog where it is wide. */
    SheetOrDialog,
}

/**
 * What an overlay destination ended up being shown as, so its content can suit the container it
 * landed in: a sheet's body fills the width and stacks its buttons, a dialog's does not.
 *
 * This is the only thing an overlay destination needs to know about the window, and it is told
 * rather than left to measure.
 */
val LocalOverlayPresentation = staticCompositionLocalOf { OverlayPresentation.Dialog }

/**
 * Renders destinations marked with [OverlaySceneStrategy.overlay] above whatever else is on the
 * back stack, as a sheet or a dialog depending on how much room the window has.
 *
 * It replaces a `NavDisplay` that lived inside a `ModalBottomSheet` and drove a private stack held
 * in a ViewModel, with the sheet-versus-dialog choice made by an `if` in the screen that opened it.
 * Both belong here instead: which container a destination gets follows from the window and from the
 * destination, and from nothing about whoever navigated to it.
 *
 * Being a strategy over overlays it must be listed before the layout strategies — the first
 * strategy to claim the topmost entry wins.
 */
class OverlaySceneStrategy<T : Any>(private val windowIsWide: Boolean) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        val preference = last.metadata[OverlayKey] ?: return null

        // An overlay has to sit above something. A back stack whose only entry is an overlay has
        // nothing to overlay, so it is left to the layout strategies to render as an ordinary
        // destination — `overlaidEntries` may not be empty.
        val overlaid = entries.dropLast(1)
        if (overlaid.isEmpty()) return null

        val presentation =
            when (preference) {
                OverlayPreference.AlwaysSheet -> OverlayPresentation.Sheet
                OverlayPreference.SheetOrDialog ->
                    if (windowIsWide) OverlayPresentation.Dialog else OverlayPresentation.Sheet
            }

        return when (presentation) {
            OverlayPresentation.Sheet -> SheetScene(last, overlaid, onBack)
            OverlayPresentation.Dialog -> DialogScene(last, overlaid)
        }
    }

    companion object {

        /** Marks an entry as an overlay, and says which container it would like. */
        fun overlay(
            preference: OverlayPreference = OverlayPreference.SheetOrDialog
        ): Map<String, Any> = metadata { put(OverlayKey, preference) }

        internal object OverlayKey : NavMetadataKey<OverlayPreference>
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T : Any> rememberOverlaySceneStrategy(): OverlaySceneStrategy<T> {
    val windowIsWide =
        currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    return remember(windowIsWide) { OverlaySceneStrategy(windowIsWide) }
}

@OptIn(ExperimentalMaterial3Api::class)
private data class SheetScene<T : Any>(
    val entry: NavEntry<T>,
    override val overlaidEntries: List<NavEntry<T>>,
    val onDismiss: () -> Unit,
) : OverlayScene<T> {

    override val key: Any = OverlaySceneKey(entry.contentKey, OverlayPresentation.Sheet)

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val previousEntries: List<NavEntry<T>> = overlaidEntries

    override val content: @Composable () -> Unit = {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            CompositionLocalProvider(LocalOverlayPresentation provides OverlayPresentation.Sheet) {
                entry.Content()
            }
        }
    }
}

/**
 * A destination shown as a dialog renders its own `AlertDialog`, which is already a window of its
 * own, so nothing is wrapped around it here: one dialog inside another would draw two scrims.
 */
private data class DialogScene<T : Any>(
    val entry: NavEntry<T>,
    override val overlaidEntries: List<NavEntry<T>>,
) : OverlayScene<T> {

    override val key: Any = OverlaySceneKey(entry.contentKey, OverlayPresentation.Dialog)

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val previousEntries: List<NavEntry<T>> = overlaidEntries

    override val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalOverlayPresentation provides OverlayPresentation.Dialog) {
            entry.Content()
        }
    }
}

/** Keeps the sheet and the dialog of one destination from being mistaken for each other. */
private data class OverlaySceneKey(val contentKey: Any, val presentation: OverlayPresentation)
