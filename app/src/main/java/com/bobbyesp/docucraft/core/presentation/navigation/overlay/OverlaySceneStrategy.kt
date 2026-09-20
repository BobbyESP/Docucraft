/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation.overlay

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
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
 * What an overlay destination is allowed to know about the container it landed in.
 *
 * This is the only thing an overlay needs to know about the window, and it is told rather than left
 * to measure. A destination that reads the window for itself gets a different answer — the device's
 * orientation, say — which stops agreeing with its container the moment the container is not the
 * whole window.
 */
@Immutable
class OverlayContext
internal constructor(
    /** Which container the destination got, so its body can suit it. */
    val presentation: OverlayPresentation,

    /**
     * Whether the container is tall enough to stack content vertically.
     *
     * A sheet is as tall as the window lets it be, so on a short window — a phone held sideways — a
     * header above a grid of actions leaves neither with room. What matters is the height that was
     * available, not which way the device is held: the two stop agreeing on anything but a phone.
     */
    val hasRoomToStack: Boolean,
) {

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is OverlayContext &&
                presentation == other.presentation &&
                hasRoomToStack == other.hasRoomToStack)

    override fun hashCode(): Int = presentation.hashCode() * 31 + hasRoomToStack.hashCode()

    override fun toString(): String =
        "OverlayContext(presentation=$presentation, hasRoomToStack=$hasRoomToStack)"
}

/** Defaults to what a composable rendered outside any overlay scene has: a full, tall window. */
val LocalOverlayContext = staticCompositionLocalOf {
    OverlayContext(OverlayPresentation.Dialog, hasRoomToStack = true)
}

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
class OverlaySceneStrategy<T : Any>(
    private val windowIsWide: Boolean,
    private val windowIsShort: Boolean,
) : SceneStrategy<T> {

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

        val context = OverlayContext(presentation, hasRoomToStack = !windowIsShort)

        return when (presentation) {
            OverlayPresentation.Sheet -> SheetScene(last, overlaid, context, onBack)
            OverlayPresentation.Dialog -> DialogScene(last, overlaid, context)
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

/**
 * The two window facts an overlay depends on, kept here so it is plain that they are two facts and
 * not one written down twice.
 *
 * Neither is the breakpoint the list-detail layout uses. "Wide enough for a centred dialog" is a
 * different question from "wide enough for a list beside a detail": one column needs the room
 * Material gives at [WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND], two need what
 * `calculatePaneScaffoldDirective` only grants at expanded — 840 dp. Between the two, an overlay is
 * a dialog over a single pane. That is deliberate: a large phone held sideways has room to centre a
 * dialog long before it has room to show two things at once.
 */
private object OverlayBreakpoints {

    /** Wide enough that a centred dialog reads better than a sheet reached with a thumb. */
    const val DIALOG_WIDTH_DP: Int = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND

    /** Tall enough that an overlay can put a header above its content and have both fit. */
    const val STACKING_HEIGHT_DP: Int = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T : Any> rememberOverlaySceneStrategy(): OverlaySceneStrategy<T> {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass

    val windowIsWide = windowSizeClass.isWidthAtLeastBreakpoint(OverlayBreakpoints.DIALOG_WIDTH_DP)
    val windowIsShort =
        !windowSizeClass.isHeightAtLeastBreakpoint(OverlayBreakpoints.STACKING_HEIGHT_DP)

    return remember(windowIsWide, windowIsShort) {
        OverlaySceneStrategy(windowIsWide, windowIsShort)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private data class SheetScene<T : Any>(
    val entry: NavEntry<T>,
    override val overlaidEntries: List<NavEntry<T>>,
    val overlayContext: OverlayContext,
    val onDismiss: () -> Unit,
) : OverlayScene<T> {

    override val key: Any = OverlaySceneKey(entry.contentKey, overlayContext)

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val previousEntries: List<NavEntry<T>> = overlaidEntries

    /**
     * Held so the sheet can be slid away before it is taken out of composition — see [onRemove].
     *
     * Assigned from the composition rather than created here because a `SheetState` has to be
     * remembered, and a scene is a plain object built outside any composition. `lateinit` is what
     * the platform's own `AnimatedBottomSheetSample` uses for exactly this.
     */
    private lateinit var sheetState: SheetState

    override val content: @Composable () -> Unit = {
        val state =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = SheetHeights,
            )
        sheetState = state

        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
            CompositionLocalProvider(LocalOverlayContext provides overlayContext) {
                entry.Content()
            }
        }
    }

    /**
     * Slides the sheet away before it leaves composition.
     *
     * Without this the sheet was only animated when the user dragged it down — because that is
     * `ModalBottomSheet` animating itself before asking to be dismissed. Every other way out (a
     * button, system back, an edit that confirmed itself) popped the entry and took the sheet off
     * screen in one frame. `NavDisplay` keeps a popped overlay composed until this returns
     * (`NavDisplay.kt:915-918`), which is the whole point of the callback.
     *
     * Guarded because a scene can be popped before it ever composed, and `hide` on a sheet the user
     * already dragged away simply returns.
     */
    override suspend fun onRemove() {
        if (::sheetState.isInitialized) sheetState.hide()
    }

    private companion object {

        /**
         * No half-open state: the sheet opens at the height of its content and back closes it.
         *
         * `ModalBottomSheet` treats back as "collapse, then dismiss" when a partially expanded
         * state exists (`ModalBottomSheet.kt:126-132`), which costs the user two presses to leave
         * one destination — and the first press is worse than wasted when the content did not fit
         * in the collapsed height to begin with, because it hides what they were reading.
         *
         * An overlay here is one entry on the back stack, so back has exactly one job: pop it. A
         * sheet whose content genuinely needs a draggable half height is a different kind of
         * destination and would have to say so; none of these is one.
         */
        val SheetHeights = setOf(SheetValue.Hidden, SheetValue.Expanded)
    }
}

/**
 * A destination shown as a dialog renders its own `AlertDialog`, which is already a window of its
 * own, so nothing is wrapped around it here: one dialog inside another would draw two scrims.
 */
private data class DialogScene<T : Any>(
    val entry: NavEntry<T>,
    override val overlaidEntries: List<NavEntry<T>>,
    val overlayContext: OverlayContext,
) : OverlayScene<T> {

    override val key: Any = OverlaySceneKey(entry.contentKey, overlayContext)

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val previousEntries: List<NavEntry<T>> = overlaidEntries

    override val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalOverlayContext provides overlayContext) { entry.Content() }
    }
}

/**
 * Keeps one destination's containers from being mistaken for each other.
 *
 * The whole context, not just the container: `NavDisplay` keeps the first overlay scene it sees for
 * a given key and ignores later ones (`NavDisplay.kt:684-691`), so anything the content is told has
 * to be part of the key or a window that changes shape mid-overlay would keep rendering what it was
 * told before it did.
 */
private data class OverlaySceneKey(val contentKey: Any, val overlayContext: OverlayContext)
