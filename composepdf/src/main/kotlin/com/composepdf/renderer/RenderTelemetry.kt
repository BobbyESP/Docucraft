package com.composepdf.renderer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.ArrayDeque

/** High-level reason that triggered a render pass. */
enum class RenderTrigger {
    DOCUMENT_LOADED,
    VIEWPORT_CHANGED,
    CONFIG_CHANGED,
    PROGRAMMATIC,
    GESTURE_DEBOUNCED,
    GESTURE_END,
    ANIMATED_ZOOM_SETTLED
}

/** One recent telemetry event recorded inside the render pipeline. */
data class RenderTelemetryEvent(
    val passId: Int,
    val source: String,
    val action: String,
    val detail: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/** Aggregate snapshot of the current render pipeline state. */
data class RenderTelemetrySnapshot(
    val lastPassId: Int = 0,
    val lastTrigger: RenderTrigger? = null,
    val lastZoom: Float = 0f,
    val lastSteppedZoom: Float = 0f,
    val lastVisiblePages: String = "",
    val lastPrefetchPages: String = "",
    val lastRequestedTiles: Int = 0,
    val lastKeepTileCount: Int = 0,
    val pageMemoryHits: Int = 0,
    val pagesRendered: Int = 0,
    val pagesPublished: Int = 0,
    val pagesDroppedAsStale: Int = 0,
    val tileMemoryHits: Int = 0,
    val tileDiskHits: Int = 0,
    val tilesRendered: Int = 0,
    val tilesPublished: Int = 0,
    val tilesDroppedAsStale: Int = 0,
    val tileJobsCancelled: Int = 0,
    val activePageJobs: Int = 0,
    val activeTileJobs: Int = 0,
    val recentEventCount: Int = 0
)

/**
 * Internal, lightweight telemetry collector for the render pipeline.
 *
 * It is intentionally in-process and dependency-free so it can be enabled in all builds without
 * affecting network, privacy, or startup performance. The main goal is to make render lifecycle
 * issues diagnosable by exposing pass metadata, cache hits, stale drops, and job cancellation patterns.
 *
 * @param maxEvents The maximum number of recent [RenderTelemetryEvent]s to keep in the circular buffer.
 */
internal class RenderTelemetry(
    private val maxEvents: Int = 200
) {
    private val eventLock = Any()
    private val recentEventsBuffer = ArrayDeque<RenderTelemetryEvent>(maxEvents)
    private val _snapshot = MutableStateFlow(RenderTelemetrySnapshot())

    val snapshot: StateFlow<RenderTelemetrySnapshot> = _snapshot.asStateFlow()

    fun recentEvents(limit: Int = 50): List<RenderTelemetryEvent> = synchronized(eventLock) {
        recentEventsBuffer.toList().takeLast(limit.coerceAtLeast(0))
    }

    fun recordPassStarted(
        passId: Int,
        trigger: RenderTrigger,
        zoom: Float,
        visiblePages: IntRange
    ) {
        _snapshot.update {
            it.copy(
                lastPassId = passId,
                lastTrigger = trigger,
                lastZoom = zoom,
                lastVisiblePages = visiblePages.toString()
            )
        }
        appendEvent(passId, "pipeline", "pass_started", "$trigger zoom=$zoom visible=$visiblePages")
    }

    fun recordTilePlan(
        passId: Int,
        steppedZoom: Float,
        keepTileCount: Int,
        requestCount: Int,
        prefetchPages: List<Int>
    ) {
        _snapshot.update {
            it.copy(
                lastSteppedZoom = steppedZoom,
                lastKeepTileCount = keepTileCount,
                lastRequestedTiles = requestCount,
                lastPrefetchPages = prefetchPages.joinToString()
            )
        }
        appendEvent(
            passId,
            "pipeline",
            "tile_plan",
            "step=$steppedZoom keep=$keepTileCount request=$requestCount prefetch=$prefetchPages"
        )
    }

    fun recordPageMemoryHit(passId: Int, pageIndex: Int, zoom: Float) {
        _snapshot.update { it.copy(pageMemoryHits = it.pageMemoryHits + 1) }
        appendEvent(passId, "page", "memory_hit", "page=$pageIndex zoom=$zoom")
    }

    fun recordPageRendered(passId: Int, pageIndex: Int, zoom: Float) {
        _snapshot.update { it.copy(pagesRendered = it.pagesRendered + 1) }
        appendEvent(passId, "page", "rendered", "page=$pageIndex zoom=$zoom")
    }

    fun recordPagePublished(passId: Int, pageIndex: Int, stale: Boolean) {
        _snapshot.update {
            it.copy(
                pagesPublished = it.pagesPublished + if (!stale) 1 else 0,
                pagesDroppedAsStale = it.pagesDroppedAsStale + if (stale) 1 else 0
            )
        }
        appendEvent(passId, "page", if (stale) "stale_drop" else "published", "page=$pageIndex")
    }

    fun recordTileMemoryHit(passId: Int, tileKey: String) {
        _snapshot.update { it.copy(tileMemoryHits = it.tileMemoryHits + 1) }
        appendEvent(passId, "tile", "memory_hit", tileKey)
    }

    fun recordTileDiskHit(passId: Int, tileKey: String) {
        _snapshot.update { it.copy(tileDiskHits = it.tileDiskHits + 1) }
        appendEvent(passId, "tile", "disk_hit", tileKey)
    }

    fun recordTileRendered(passId: Int, tileKey: String) {
        _snapshot.update { it.copy(tilesRendered = it.tilesRendered + 1) }
        appendEvent(passId, "tile", "rendered", tileKey)
    }

    fun recordTilePublished(passId: Int, tileKey: String, stale: Boolean) {
        _snapshot.update {
            it.copy(
                tilesPublished = it.tilesPublished + if (!stale) 1 else 0,
                tilesDroppedAsStale = it.tilesDroppedAsStale + if (stale) 1 else 0
            )
        }
        appendEvent(passId, "tile", if (stale) "stale_drop" else "published", tileKey)
    }

    fun recordTileJobCancelled(tileKey: String) {
        _snapshot.update { it.copy(tileJobsCancelled = it.tileJobsCancelled + 1) }
        appendEvent(_snapshot.value.lastPassId, "tile", "cancelled", tileKey)
    }

    fun recordActiveJobs(pageJobs: Int, tileJobs: Int) {
        _snapshot.update { it.copy(activePageJobs = pageJobs, activeTileJobs = tileJobs) }
    }

    private fun appendEvent(passId: Int, source: String, action: String, detail: String) {
        synchronized(eventLock) {
            if (recentEventsBuffer.size == maxEvents) {
                recentEventsBuffer.removeFirst()
            }
            recentEventsBuffer.addLast(
                RenderTelemetryEvent(
                    passId = passId,
                    source = source,
                    action = action,
                    detail = detail
                )
            )
            _snapshot.update { it.copy(recentEventCount = recentEventsBuffer.size) }
        }
    }
}
