/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation.pane

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
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
 * The strategy knows, because the strategy is what put the destination there. So the strategy
 * answers — see [sharingTheWindow].
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
 * Defaults to filling the window, which is what a destination outside any multi-pane scene does:
 * the single-pane fallback `NavDisplay` reaches for when no strategy claims the stack, and also
 * `PdfViewerActivity` or a preview, neither of which has a scene at all.
 */
val LocalPaneContext = staticCompositionLocalOf { PaneContext(isSolePane = true) }

/**
 * Wraps [this] so the destinations in every scene it produces are told they are sharing the window.
 *
 * Only correct for a strategy that claims the stack **only** when it really does lay destinations
 * out side by side. `ListDetailSceneStrategy` is such a strategy as long as
 * `shouldHandleSinglePaneLayout` stays false: it builds its scaffold, then returns null unless
 * `paneCount` came out above one (`ListDetailSceneStrategy.kt:192-196`). Anything it hands back is
 * therefore two panes on screen, and anything it declines falls through to the single-pane
 * fallback, where [LocalPaneContext]'s own default is already the right answer.
 *
 * This replaces asking the *scene* how many entries it was rendering, which was a guess and a wrong
 * one in a case that shows up on every tablet: a list alone on a wide window is one entry inside a
 * two-pane scaffold, because the second pane is filled by the list's `detailPlaceholder` and a
 * placeholder is not an entry. Counting entries called that a sole pane. The two library scene
 * types that would have answered honestly are both `internal`, so the question cannot be asked
 * after the fact — it has to be asked of whoever made the decision.
 */
fun <T : Any> SceneStrategy<T>.sharingTheWindow(): SceneStrategy<T> =
    SharedWindowSceneStrategy(this)

internal class SharedWindowSceneStrategy<T : Any>(private val delegate: SceneStrategy<T>) :
    SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val scope = this

        return with(delegate) { scope.calculateScene(entries) }
            ?.let { PaneAwareScene(it, SharedPane) }
    }

    private companion object {
        val SharedPane = PaneContext(isSolePane = false)
    }
}

/**
 * A scene's identity is `(its class, its key)`, and wrapping would otherwise make every wrapped
 * scene the same class — collapsing scenes that differ only by type into one identity, so
 * `NavDisplay` would stop animating between them. Folding the wrapped scene's class into the key
 * keeps identity exactly as precise as it was.
 */
internal class PaneAwareScene<T : Any>(
    private val delegate: Scene<T>,
    private val paneContext: PaneContext,
) : Scene<T> by delegate {

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
