/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import java.util.PriorityQueue
import kotlinx.coroutines.channels.Channel

/**
 * Priority queue of pending [RenderWork] that supports atomic wholesale replacement.
 *
 * Cancellation is implicit: every plan pass replaces the queue contents, so work that is no longer
 * wanted simply ceases to exist. Workers additionally re-validate against the live plan before
 * rendering, which covers work that was already dequeued.
 */
internal class WorkQueue(workerCount: Int) {
    private val lock = Any()
    private val comparator = compareBy<RenderWork>({ it.band }, { it.distance })
    private var heap = PriorityQueue(16, comparator)
    private var epoch = 0
    private val signal = Channel<Unit>(capacity = workerCount)
    private val workerCount = workerCount

    /** Replaces all pending work with [work] tagged with [epoch], waking idle workers. */
    fun replace(work: List<RenderWork>, epoch: Int) {
        synchronized(lock) {
            heap = PriorityQueue(work.size.coerceAtLeast(16), comparator)
            heap.addAll(work)
            this.epoch = epoch
        }
        repeat(workerCount) { signal.trySend(Unit) }
    }

    fun clear() {
        synchronized(lock) { heap.clear() }
    }

    /** Suspends until work is available, then returns the highest-priority item and its epoch. */
    suspend fun take(): Pair<RenderWork, Int> {
        while (true) {
            synchronized(lock) {
                val next = heap.poll()
                if (next != null) return next to epoch
            }
            signal.receive()
        }
    }

    /**
     * Removes and returns up to [max] more queued tiles belonging to [pageIndex], best-first. Used
     * to batch several tiles of the same page under a single page open.
     */
    fun drainTilesForPage(pageIndex: Int, max: Int): List<RenderWork.Tile> {
        if (max <= 0) return emptyList()
        synchronized(lock) {
            var matches: ArrayList<RenderWork.Tile>? = null
            for (work in heap) {
                if (work is RenderWork.Tile && work.pageIndex == pageIndex) {
                    (matches ?: ArrayList<RenderWork.Tile>(max).also { matches = it }).add(work)
                }
            }
            val found = matches ?: return emptyList()
            found.sortWith(comparator)
            val batch = if (found.size > max) found.subList(0, max) else found
            heap.removeAll(batch.toSet())
            return batch.toList()
        }
    }
}
