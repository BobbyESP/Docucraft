package com.composepdf.internal.logic.tiles

import android.graphics.Rect
import com.composepdf.internal.service.pdf.PageRenderer.Companion.TILE_SIZE
import kotlin.math.roundToInt

/**
 * Represents a unique identifier for a specific tile within a PDF document.
 *
 * This key encapsulates all parameters required to identify, cache, and retrieve a rendered
 * image segment of a page. It is used across the entire rendering pipeline, including
 * tile planning, memory caching, and disk persistence.
 *
 * @property pageIndex The zero-based index of the page in the PDF document.
 * @property rect The boundaries of the tile in pixels relative to the page at the given zoom level.
 * @property zoom The magnification level at which the tile was rendered.
 * @property baseWidthKey A normalized integer representation of the page's base width, used to
 * invalidate caches when the layout or orientation changes.
 */
internal data class TileKey(
    val pageIndex: Int,
    val rect: Rect,
    val zoom: Float,
    val baseWidthKey: Int = UNKNOWN_BASE_WIDTH_KEY
) {
    val tileX: Int
        get() = rect.left / TILE_SIZE

    val tileY: Int
        get() = rect.top / TILE_SIZE

    fun toCacheKey(): String {
        val normalizedZoom = normalizedZoom(zoom)
        return "${pageIndex}_${rect.left}_${rect.top}_${rect.right}_${rect.bottom}_${normalizedZoom}_${
            normalizedBaseWidthKey(
                baseWidthKey
            )
        }"
    }

    fun toDiskCacheKey(documentKey: String): String =
        if (documentKey.isNotEmpty()) "${documentKey}_${toCacheKey()}" else toCacheKey()

    companion object {
        const val BASE_LAYER_ZOOM = 1.0f

        fun fromCacheKey(key: String): TileKey? {
            val parts = key.split("_")
            if (parts.size !in 6..7) return null

            val page = parts[0].toIntOrNull() ?: return null
            val left = parts[1].toIntOrNull() ?: return null
            val top = parts[2].toIntOrNull() ?: return null
            val right = parts[3].toIntOrNull() ?: return null
            val bottom = parts[4].toIntOrNull() ?: return null
            val zoom = parts[5].toFloatOrNull() ?: return null
            val baseWidthKey = parts.getOrNull(6)?.toIntOrNull() ?: UNKNOWN_BASE_WIDTH_KEY

            return TileKey(page, Rect(left, top, right, bottom), zoom, baseWidthKey)
        }

        fun fromLayout(pageIndex: Int, rect: Rect, zoom: Float, baseWidth: Float): TileKey =
            TileKey(
                pageIndex = pageIndex,
                rect = rect,
                zoom = zoom,
                baseWidthKey = normalizedBaseWidthKey(baseWidth)
            )

        fun normalizedBaseWidthKey(baseWidth: Float): Int = (baseWidth * 100f).roundToInt()

        private fun normalizedBaseWidthKey(baseWidthKey: Int): Int =
            if (baseWidthKey == UNKNOWN_BASE_WIDTH_KEY) UNKNOWN_BASE_WIDTH_KEY else baseWidthKey

        private fun normalizedZoom(zoom: Float): Float = (zoom * 100f).roundToInt() / 100f


        private const val UNKNOWN_BASE_WIDTH_KEY = -1
    }
}