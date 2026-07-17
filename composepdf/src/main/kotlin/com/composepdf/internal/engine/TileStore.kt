/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Everything the UI needs to draw one tile. Rect coordinates are level-space pixels. */
internal class TileDraw(
    val id: TileId,
    val image: ImageBitmap,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val levelScale: Float,
) {
    val level: Int
        get() = id.level
}

/**
 * The single owner of every rendered tile bitmap.
 *
 * Responsibilities:
 * - **Cache**: byte-bounded LRU over all rendered tiles, so zooming back to a recent level is
 *   instant.
 * - **Publication**: exposes an immutable per-page snapshot ([published]) of the tiles the UI
 *   should draw right now. During a zoom change, tiles from other levels that still intersect the
 *   visible region stay published (scaled by the UI) until the current level fully covers the page
 *   — this is what makes zooming seamless instead of blanking.
 * - **Bitmap ownership**: evicted bitmaps that were never shown go straight back to the
 *   [BitmapPool]; bitmaps that were published are parked in a small time-delayed graveyard first so
 *   a frame that is mid-draw can never observe a reconfigured bitmap.
 *
 * All state is guarded by a single lock; methods are called from engine threads only.
 */
internal class TileStore(
    private val pool: BitmapPool,
    private val maxBytes: Long = defaultMaxBytes(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Entry(
        val draw: TileDraw,
        val bitmap: Bitmap,
        val bytes: Int,
        var published: Boolean = false,
        var everPublished: Boolean = false,
    )

    private val lock = Any()
    private val entries = LinkedHashMap<Long, Entry>(64, 0.75f, true)
    private var currentBytes = 0L
    private var lastPlan: RenderPlan = RenderPlan.EMPTY
    private val graveyard = ArrayDeque<Pair<Long, Bitmap>>()

    private val _published = MutableStateFlow<Map<Int, List<TileDraw>>>(emptyMap())
    val published: StateFlow<Map<Int, List<TileDraw>>> = _published

    /** True when the tile is rendered and cached (also refreshes its LRU position). */
    fun contains(packedId: Long): Boolean = synchronized(lock) { entries[packedId] != null }

    /** Registers a freshly rendered tile and republishes its page if it is currently relevant. */
    fun put(id: TileId, rect: TileRect, levelScale: Float, bitmap: Bitmap) {
        synchronized(lock) {
            val existing = entries.remove(id.packed)
            if (existing != null) {
                currentBytes -= existing.bytes
                retire(existing)
            }
            val draw =
                TileDraw(
                    id = id,
                    image = bitmap.asImageBitmap(),
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom,
                    levelScale = levelScale,
                )
            val entry = Entry(draw, bitmap, bitmap.allocationByteCount)
            entries[id.packed] = entry
            currentBytes += entry.bytes
            evictOverBudgetLocked()
            drainGraveyardLocked()

            if (isRelevantToPlanLocked(id)) {
                publishLocked()
            }
        }
    }

    /** Applies a new plan: recompute what is published and trim the cache. */
    fun applyPlan(plan: RenderPlan) {
        synchronized(lock) {
            lastPlan = plan
            // Touch desired tiles so LRU eviction prefers everything else.
            for (desired in plan.desiredTilesByPage.values) {
                for (packed in desired) entries[packed]
            }
            publishLocked()
            evictOverBudgetLocked()
            drainGraveyardLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            for (entry in entries.values) {
                currentBytes -= entry.bytes
                retire(entry)
            }
            entries.clear()
            currentBytes = 0
            lastPlan = RenderPlan.EMPTY
            _published.value = emptyMap()
            drainGraveyardLocked()
        }
    }

    private fun isRelevantToPlanLocked(id: TileId): Boolean {
        val plan = lastPlan
        if (plan.desiredTilesByPage[id.pageIndex]?.contains(id.packed) == true) return true
        // A tile at another level can still matter as a stale fallback for a visible page.
        return plan.tileLevel != null &&
            id.level != plan.tileLevel &&
            plan.visibleRectByPage.containsKey(id.pageIndex)
    }

    /**
     * Rebuilds the published snapshot from [lastPlan]:
     * - current-level tiles that are desired and rendered;
     * - stale-level tiles intersecting the visible rect of their page, kept only while the current
     *   level does not yet fully cover that page, ordered so lower resolutions draw underneath.
     */
    private fun publishLocked() {
        val plan = lastPlan
        val currentLevel = plan.tileLevel
        if (currentLevel == null) {
            if (_published.value.isNotEmpty()) _published.value = emptyMap()
            for (entry in entries.values) entry.published = false
            return
        }

        val result = HashMap<Int, List<TileDraw>>()
        val newlyPublished = HashSet<Long>()

        val pages = plan.desiredTilesByPage.keys + plan.visibleRectByPage.keys
        for (page in pages) {
            val desired = plan.desiredTilesByPage[page].orEmpty()
            val current = ArrayList<TileDraw>(desired.size)
            for (packed in desired) {
                val entry = entries[packed] ?: continue
                current.add(entry.draw)
            }
            val complete = desired.isNotEmpty() && current.size == desired.size

            val stale =
                if (!complete) {
                    val visibleRect = plan.visibleRectByPage[page]
                    if (visibleRect != null) collectStaleLocked(page, currentLevel, visibleRect)
                    else emptyList()
                } else {
                    emptyList()
                }

            if (current.isEmpty() && stale.isEmpty()) continue
            val drawList = ArrayList<TileDraw>(stale.size + current.size)
            drawList += stale
            drawList += current
            result[page] = drawList
            for (draw in drawList) newlyPublished.add(draw.id.packed)
        }

        for ((packed, entry) in entries) {
            val nowPublished = packed in newlyPublished
            entry.published = nowPublished
            if (nowPublished) entry.everPublished = true
        }
        _published.value = result
    }

    private fun collectStaleLocked(
        page: Int,
        currentLevel: Int,
        visibleRect: TileRect,
    ): List<TileDraw> {
        var stale: ArrayList<TileDraw>? = null
        for (entry in entries.values) {
            val draw = entry.draw
            if (draw.id.pageIndex != page || draw.level == currentLevel) continue
            // Intersect in layout base space (level-space rect divided by the level's scale).
            val inv = 1f / draw.levelScale
            val left = draw.left * inv
            val top = draw.top * inv
            val right = draw.right * inv
            val bottom = draw.bottom * inv
            if (
                right > visibleRect.left &&
                    left < visibleRect.right &&
                    bottom > visibleRect.top &&
                    top < visibleRect.bottom
            ) {
                (stale ?: ArrayList<TileDraw>(8).also { stale = it }).add(draw)
            }
        }
        return stale?.apply { sortBy { it.level } } ?: emptyList()
    }

    private fun evictOverBudgetLocked() {
        if (currentBytes <= maxBytes) return
        val iterator = entries.entries.iterator()
        while (currentBytes > maxBytes && iterator.hasNext()) {
            val (packed, entry) = iterator.next()
            if (entry.published) continue
            if (lastPlan.desiredTilesByPage[TileId(packed).pageIndex]?.contains(packed) == true) {
                continue
            }
            iterator.remove()
            currentBytes -= entry.bytes
            retire(entry)
        }
    }

    private fun retire(entry: Entry) {
        if (entry.everPublished) {
            graveyard.addLast(clock() to entry.bitmap)
            // Never let the graveyard grow unbounded; overflow goes to the GC.
            while (graveyard.size > MAX_GRAVEYARD_SIZE) graveyard.pollFirst()
        } else {
            pool.put(entry.bitmap)
        }
    }

    private fun drainGraveyardLocked() {
        val cutoff = clock() - GRAVEYARD_GRACE_MS
        while (true) {
            val head = graveyard.peekFirst() ?: return
            if (head.first > cutoff) return
            graveyard.pollFirst()
            pool.put(head.second)
        }
    }

    companion object {
        /**
         * Once-published bitmaps rest this long before pool reuse, so in-flight frames stay safe.
         */
        const val GRAVEYARD_GRACE_MS = 300L
        const val MAX_GRAVEYARD_SIZE = 64

        fun defaultMaxBytes(): Long =
            (Runtime.getRuntime().maxMemory() / 4).coerceAtMost(128L * 1024 * 1024)
    }
}
