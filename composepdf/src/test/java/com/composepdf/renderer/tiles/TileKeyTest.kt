package com.composepdf.renderer.tiles

import android.graphics.Rect
import com.composepdf.internal.logic.tiles.TileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileKeyTest {

    @Test
    fun cacheKey_changesWhenBasePageGeometryChanges() {
        val left = TileKey.fromLayout(
            pageIndex = 0,
            rect = Rect(0, 0, 256, 256),
            zoom = 1.25f,
            baseWidth = 500f
        )
        val right = TileKey.fromLayout(
            pageIndex = 0,
            rect = Rect(0, 0, 256, 256),
            zoom = 1.25f,
            baseWidth = 520f
        )

        assertNotEquals(left.toCacheKey(), right.toCacheKey())
    }

    @Test
    fun fromCacheKey_remainsBackwardCompatibleWithLegacyKeys() {
        val parsed = TileKey.fromCacheKey("1_0_0_256_256_1.25")

        assertEquals(1, parsed?.pageIndex)
        assertEquals(1.25f, parsed?.zoom ?: 0f, 0.001f)
        assertTrue((parsed?.baseWidthKey ?: 0) < 0)
    }
}

