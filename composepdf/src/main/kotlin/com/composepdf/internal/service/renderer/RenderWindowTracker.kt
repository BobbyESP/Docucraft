package com.composepdf.internal.service.renderer

import com.composepdf.internal.logic.tiles.TileKey
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable snapshot of the current rendering window.
 */
internal data class RenderWindowSnapshot(
    val sessionToken: Int,
    val desiredPages: Map<Int, TileKey>,
    val desiredTiles: Set<String>
)

/**
 * Manages the state of the active rendering window and coordinates work across threads.
 *
 * This tracker maintains a thread-safe, atomic snapshot of the pages and tiles currently
 * visible or required by the UI. It serves as the source of truth for background workers
 * to determine which rendering tasks are still relevant and prevents the publication of
 * stale results from previous sessions or scrolled-away regions.
 */
internal class RenderWindowTracker {
    private val snapshot = AtomicReference(
        RenderWindowSnapshot(
            sessionToken = 0,
            desiredPages = emptyMap(),
            desiredTiles = emptySet()
        )
    )

    fun getSnapshot(): RenderWindowSnapshot = snapshot.get()

    fun currentSessionToken(): Int = snapshot.get().sessionToken

    fun beginNewSession(): Int {
        var old: RenderWindowSnapshot
        var next: RenderWindowSnapshot
        do {
            old = snapshot.get()
            next = RenderWindowSnapshot(
                sessionToken = old.sessionToken + 1,
                desiredPages = emptyMap(),
                desiredTiles = emptySet()
            )
        } while (!snapshot.compareAndSet(old, next))
        return next.sessionToken
    }

    fun updateDesiredPages(specs: Map<Int, TileKey>) {
        var old: RenderWindowSnapshot
        var next: RenderWindowSnapshot
        do {
            old = snapshot.get()
            next = old.copy(desiredPages = specs.toMap())
        } while (!snapshot.compareAndSet(old, next))
    }

    fun updateDesiredTiles(tileKeys: Set<String>) {
        var old: RenderWindowSnapshot
        var next: RenderWindowSnapshot
        do {
            old = snapshot.get()
            next = old.copy(desiredTiles = tileKeys.toSet())
        } while (!snapshot.compareAndSet(old, next))
    }

    /**
     * Final publication check. Returns true if the session and key are still current.
     */
    fun shouldPublishPage(session: Int, pageIndex: Int, cacheKey: TileKey): Boolean {
        val current = snapshot.get()
        return session == current.sessionToken && current.desiredPages[pageIndex] == cacheKey
    }

    /**
     * Final publication check for high-res tiles.
     */
    fun shouldPublishTile(session: Int, tileKey: String): Boolean {
        val current = snapshot.get()
        return session == current.sessionToken && tileKey in current.desiredTiles
    }
}
