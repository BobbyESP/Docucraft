package com.composepdf.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import com.composepdf.cache.BitmapPool

/**
 * Renders individual PDF pages to bitmaps with support for scaling and color transformations.
 * 
 * This class handles the actual rendering of PDF pages, applying zoom levels, DPI scaling,
 * and optional night mode inversion. It uses a [BitmapPool] to minimize memory allocations.
 * 
 * @property bitmapPool Pool for recycling bitmaps
 */
class PageRenderer(
    private val bitmapPool: BitmapPool
) {
    
    private val nightModePaint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    
    /**
     * Configuration for rendering a page.
     * 
     * @property zoomLevel The zoom factor (1.0 = 100%)
     * @property renderQuality DPI multiplier for render quality (1.0 = screen DPI)
     * @property nightMode Whether to invert colors for night mode
     * @property backgroundColor The background color (default is white)
     */
    data class RenderConfig(
        val zoomLevel: Float = 1f,
        val renderQuality: Float = 1.5f,
        val nightMode: Boolean = false,
        val backgroundColor: Int = android.graphics.Color.WHITE
    )
    
    /**
     * Renders a PDF page to a bitmap.
     * 
     * The bitmap dimensions are calculated based on the page size, zoom level,
     * and render quality settings. The resulting bitmap is obtained from the
     * [BitmapPool] when possible.
     * 
     * @param page The PDF page to render
     * @param config The rendering configuration
     * @return A rendered bitmap of the page
     */
    fun render(page: PdfRenderer.Page, config: RenderConfig): Bitmap {
        // Calculate render dimensions
        val scale = config.zoomLevel * config.renderQuality
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        
        // Get or create bitmap
        val bitmap = bitmapPool.get(width, height, Bitmap.Config.ARGB_8888)
        
        // Fill with background color
        bitmap.eraseColor(config.backgroundColor)
        
        // Create transformation matrix
        val matrix = Matrix().apply {
            setScale(scale, scale)
        }
        
        // Render the page
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        
        // Apply night mode if enabled
        return if (config.nightMode) {
            applyNightMode(bitmap)
        } else {
            bitmap
        }
    }
    
    /**
     * Calculates the dimensions that a rendered bitmap would have.
     * 
     * @param pageWidth The page width in points
     * @param pageHeight The page height in points
     * @param config The rendering configuration
     * @return The pixel dimensions of the rendered bitmap
     */
    fun calculateRenderSize(
        pageWidth: Int,
        pageHeight: Int,
        config: RenderConfig
    ): Pair<Int, Int> {
        val scale = config.zoomLevel * config.renderQuality
        val width = (pageWidth * scale).toInt().coerceAtLeast(1)
        val height = (pageHeight * scale).toInt().coerceAtLeast(1)
        return width to height
    }
    
    private fun applyNightMode(source: Bitmap): Bitmap {
        val sourceConfig = source.config ?: Bitmap.Config.ARGB_8888
        val result = bitmapPool.get(source.width, source.height, sourceConfig)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, nightModePaint)
        
        // Return source bitmap to pool
        bitmapPool.put(source)
        
        return result
    }
}
