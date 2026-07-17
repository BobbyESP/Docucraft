/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.ArrayDeque
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the low-resolution base bitmap of each page.
 *
 * A page's base bitmap is the always-available fallback the UI stretches to the page bounds, so a
 * page is never blank while tiles render. Bitmaps are keyed by page and target width; when the
 * desired width changes (zoom-out step, layout change) the old bitmap stays published until the
 * replacement arrives, then is retired.
 */
internal class PageBitmapStore(
    private val pool: BitmapPool,
    private val maxBytes: Long = defaultMaxBytes(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Entry(
        val width: Int,
        val bitmap: Bitmap,
        val bytes: Int,
        var everPublished: Boolean,
    ) {
        /** Wrapped once here so the UI never allocates while drawing. */
        val image: ImageBitmap = bitmap.asImageBitmap()
    }

    private val lock = Any()
    private val entries = HashMap<Int, Entry>()
    private var currentBytes = 0L
    private var lastPlan: RenderPlan = RenderPlan.EMPTY
    private val graveyard = ArrayDeque<Pair<Long, Bitmap>>()

    private val _published = MutableStateFlow<Map<Int, ImageBitmap>>(emptyMap())
    val published: StateFlow<Map<Int, ImageBitmap>> = _published

    /** Width of the cached bitmap for [pageIndex], or null when none exists. */
    fun widthOf(pageIndex: Int): Int? = synchronized(lock) { entries[pageIndex]?.width }

    fun put(pageIndex: Int, width: Int, bitmap: Bitmap) {
        synchronized(lock) {
            val existing = entries[pageIndex]
            val desiredWidth = lastPlan.desiredBaseWidths[pageIndex]
            // Never replace a bitmap that already matches the plan with one that does not
            // (a late result from an outdated pass).
            if (existing != null && existing.width == desiredWidth && width != desiredWidth) {
                pool.put(bitmap)
                return
            }
            if (existing != null) {
                currentBytes -= existing.bytes
                retire(existing)
            }
            val entry = Entry(width, bitmap, bitmap.allocationByteCount, everPublished = false)
            entries[pageIndex] = entry
            currentBytes += entry.bytes
            evictOverBudgetLocked()
            publishLocked()
            drainGraveyardLocked()
        }
    }

    fun applyPlan(plan: RenderPlan) {
        synchronized(lock) {
            lastPlan = plan
            // Drop pages that left the keep window entirely.
            if (!plan.baseWindow.isEmpty()) {
                val iterator = entries.entries.iterator()
                while (iterator.hasNext()) {
                    val (page, entry) = iterator.next()
                    if (page !in plan.baseWindow) {
                        iterator.remove()
                        currentBytes -= entry.bytes
                        retire(entry)
                    }
                }
            }
            evictOverBudgetLocked()
            publishLocked()
            drainGraveyardLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            for (entry in entries.values) retire(entry)
            entries.clear()
            currentBytes = 0
            lastPlan = RenderPlan.EMPTY
            _published.value = emptyMap()
            drainGraveyardLocked()
        }
    }

    private fun publishLocked() {
        val snapshot = HashMap<Int, ImageBitmap>(entries.size)
        for ((page, entry) in entries) {
            snapshot[page] = entry.image
            entry.everPublished = true
        }
        _published.value = snapshot
    }

    /** Evicts the pages farthest from the visible range until the budget is met. */
    private fun evictOverBudgetLocked() {
        if (currentBytes <= maxBytes || lastPlan.visiblePages.isEmpty()) return
        val visible = lastPlan.visiblePages
        val candidates =
            entries.keys
                .filter { it !in visible }
                .sortedByDescending { page ->
                    minOf(abs(page - visible.first), abs(page - visible.last))
                }
        for (page in candidates) {
            if (currentBytes <= maxBytes) break
            val entry = entries.remove(page) ?: continue
            currentBytes -= entry.bytes
            retire(entry)
        }
    }

    private fun retire(entry: Entry) {
        if (entry.everPublished) {
            graveyard.addLast(clock() to entry.bitmap)
            while (graveyard.size > MAX_GRAVEYARD_SIZE) graveyard.pollFirst()
        } else {
            pool.put(entry.bitmap)
        }
    }

    private fun drainGraveyardLocked() {
        val cutoff = clock() - TileStore.GRAVEYARD_GRACE_MS
        while (true) {
            val head = graveyard.peekFirst() ?: return
            if (head.first > cutoff) return
            graveyard.pollFirst()
            pool.put(head.second)
        }
    }

    companion object {
        const val MAX_GRAVEYARD_SIZE = 16

        fun defaultMaxBytes(): Long =
            (Runtime.getRuntime().maxMemory() / 4).coerceAtMost(96L * 1024 * 1024)
    }
}
