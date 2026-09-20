/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation.pane

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import kotlin.reflect.KClass

/**
 * What a destination is allowed to know about the layout it landed in.
 *
 * Screens used to answer this for themselves by measuring the window —
 * `currentWindowAdaptiveInfoV2` and a breakpoint comparison, written out twice in two features —
 * and then turning a width into a decision about their own chrome. That is the wrong question asked
 * in the wrong place: a destination cannot see whether anything is beside it, only how wide the
 * window is, and the two stop agreeing the moment a destination fills a wide window on its own.
 *
 * The scene knows, because the scene is what put the destination there. So the scene answers.
 */
@Immutable
class PaneContext
internal constructor(
    /** Whether this destination has the window to itself. */
    val isSolePane: Boolean
) {

    /**
     * Whether the destination has to offer its own way back.
     *
     * Sharing the window means whatever it is sharing with — a list, typically — is still on screen
     * and still reachable, so a back affordance would be pointing at something already visible.
     * Filling the window means there is nothing else to touch.
     */
    val providesOwnBackAffordance: Boolean
        get() = isSolePane

    override fun equals(other: Any?): Boolean =
        this === other || (other is PaneContext && isSolePane == other.isSolePane)

    override fun hashCode(): Int = isSolePane.hashCode()

    override fun toString(): String = "PaneContext(isSolePane=$isSolePane)"
}

/**
 * Defaults to filling the window, which is what a composable rendered outside any scene does —
 * `PdfViewerActivity`, or a preview.
 */
val LocalPaneContext = staticCompositionLocalOf { PaneContext(isSolePane = true) }

/**
 * Tells every destination in a scene how that scene is laying it out, so none of them has to guess.
 *
 * Pass to `NavDisplay(sceneDecoratorStrategies = ...)`. Overlay scenes — dialogs, sheets — are
 * never handed to a decorator by `NavDisplay`, so they keep the surrounding value.
 */
fun <T : Any> paneContextSceneDecorator(): SceneDecoratorStrategy<T> =
    SceneDecoratorStrategy { scene ->
        PaneAwareScene(scene)
    }

/**
 * A scene's identity is `(its class, its key)`, and wrapping would otherwise make every scene in
 * the app the same class — collapsing a single pane and a list-detail pair into one identity, so
 * `NavDisplay` would stop animating between them. Folding the wrapped scene's class into the key
 * keeps identity exactly as precise as it was.
 */
internal class PaneAwareScene<T : Any>(private val delegate: Scene<T>) : Scene<T> by delegate {

    private val paneContext = PaneContext(isSolePane = delegate.entries.size <= 1)

    /** Exposed for tests: what this scene will tell the destinations it renders. */
    internal val paneContextForTest: PaneContext
        get() = paneContext

    override val key: Any = WrappedKey(delegate::class, delegate.key)

    override val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalPaneContext provides paneContext) { delegate.content() }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is PaneAwareScene<*> &&
                delegate == other.delegate &&
                paneContext == other.paneContext)

    override fun hashCode(): Int = delegate.hashCode() * 31 + paneContext.hashCode()

    override fun toString(): String = "PaneAwareScene(paneContext=$paneContext, scene=$delegate)"

    internal data class WrappedKey(val sceneClass: KClass<*>, val sceneKey: Any)
}
