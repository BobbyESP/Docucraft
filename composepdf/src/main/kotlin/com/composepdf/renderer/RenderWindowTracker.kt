package com.composepdf.renderer

import com.composepdf.cache.PageCacheKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks the currently desired render outputs for the active viewer session.
 *
 * The scheduler reuses in-flight work aggressively. A page or tile job started by an older render
 * pass can still be correct for the newest viewport if its key remains part of the current desired
 * window. This tracker is the single source of truth for that rule:
 * - page publication is allowed only when the same [PageCacheKey] is still wanted for that page
 * - tile publication is allowed only when the tile key is still inside the latest keep window
 * - a session token invalidates all pending work across document changes/reset
 */
internal class RenderWindowTracker {
    private val sessionToken = AtomicInteger(0)
    private val desiredPages = ConcurrentHashMap<Int, PageCacheKey>()
    private val desiredTiles = ConcurrentHashMap.newKeySet<String>()

    fun currentSessionToken(): Int = sessionToken.get()

    fun beginNewSession(): Int {
        desiredPages.clear()
        desiredTiles.clear()
        return sessionToken.incrementAndGet()
    }

    fun updateDesiredPages(specs: Map<Int, PageCacheKey>) {
        desiredPages.clear()
        desiredPages.putAll(specs)
    }

    fun updateDesiredTiles(tileKeys: Set<String>) {
        desiredTiles.clear()
        desiredTiles.addAll(tileKeys)
    }

    fun shouldPublishPage(session: Int, pageIndex: Int, cacheKey: PageCacheKey): Boolean =
        session == sessionToken.get() && desiredPages[pageIndex] == cacheKey

    fun shouldPublishTile(session: Int, tileKey: String): Boolean =
        session == sessionToken.get() && tileKey in desiredTiles
}

