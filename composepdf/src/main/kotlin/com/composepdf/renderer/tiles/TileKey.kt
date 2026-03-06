package com.composepdf.renderer.tiles

import android.graphics.Rect
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
    val zoom: Float
) {
    fun toCacheKey(): String {
        val normalizedZoom = normalizedZoom(zoom)
        return "${pageIndex}_${rect.left}_${rect.top}_${rect.right}_${rect.bottom}_$normalizedZoom"
    }

    fun toDiskCacheKey(documentKey: String): String =
        if (documentKey.isNotEmpty()) "${documentKey}_${toCacheKey()}" else toCacheKey()

    companion object {
        fun fromCacheKey(key: String): TileKey? {
            val parts = key.split("_")
            if (parts.size != 6) return null

            val page = parts[0].toIntOrNull() ?: return null
            val left = parts[1].toIntOrNull() ?: return null
            val top = parts[2].toIntOrNull() ?: return null
            val right = parts[3].toIntOrNull() ?: return null
            val bottom = parts[4].toIntOrNull() ?: return null
            val zoom = parts[5].toFloatOrNull() ?: return null

            return TileKey(page, Rect(left, top, right, bottom), zoom)
        }

        private fun normalizedZoom(zoom: Float): Float = (zoom * 100f).roundToInt() / 100f
    }
}