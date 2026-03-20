package com.composepdf

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
