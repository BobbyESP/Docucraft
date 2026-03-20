package com.composepdf.internal.logic.tiles

import android.graphics.Rect
import com.composepdf.internal.service.pdf.PageRenderer.Companion.TILE_SIZE
import kotlin.math.roundToInt

/**
 * Unique identifier for a rendered PDF tile.
 *
 * The same typed key now drives the full pipeline:
 * - tile planning
 * - in-memory cache lookup
 * - disk-cache namespacing
 * - UI decoding for composition
 *
 * Keeping the format in one place avoids stringly-typed duplication across the system.
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