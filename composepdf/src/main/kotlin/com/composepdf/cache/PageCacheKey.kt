package com.composepdf.cache

/**
 * A unique identifier used as a key for caching rendered PDF pages.
 *
 * This data class encapsulates the necessary parameters to distinguish between different
 * cached page images, typically including the document source, the page index, and
 * rendering dimensions or quality settings.
 *
 * @property pageIndex The zero-based index of the page within the document.
 * @property zoomLevel The zoom level used for rendering.
 * @property width The width of the rendered page in pixels.
 * @property height The height of the rendered page in pixels.
 */
data class PageCacheKey(
    val pageIndex: Int,
    val zoomLevel: Float,
    val width: Int,
    val height: Int
)
