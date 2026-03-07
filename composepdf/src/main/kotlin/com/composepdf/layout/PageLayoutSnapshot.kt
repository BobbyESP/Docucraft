package com.composepdf.layout

import android.util.Size
import com.composepdf.state.FitMode
import kotlin.math.min

/**
 * An immutable snapshot of the document layout calculated at a base zoom level of `1f`.
 *
 * This class serves as a pure, side-effect-free representation of the document geometry.
 * It is responsible for translating document coordinates to screen coordinates and performing
 * spatial queries such as visibility detection, hit testing, and pan clamping.
 *
 * The layout is recalculated and a new snapshot is emitted whenever the viewport dimensions,
 * page list, spacing, or [FitMode] change.
 *
 * @property pageSizes The original dimensions of the PDF pages.
 * @property pageTops The vertical offsets (Y) of each page in document space.
 * @property pageHeights The calculated heights of each page after applying [FitMode].
 * @property pageWidths The calculated widths of each page after applying [FitMode].
 * @property totalDocumentHeight The total height of the scrollable area, including spacing.
 * @property maxPageWidth The width of the widest page in the layout, used for horizontal centering.
 * @property viewport The dimensions of the visible component area.
 * @property pageSpacingPx The vertical gap between pages in pixels.
 */
internal class PageLayoutSnapshot(
    val pageSizes: List<Size>,
    val pageTops: FloatArray,
    val pageHeights: FloatArray,
    val pageWidths: FloatArray,
    val totalDocumentHeight: Float,
    val maxPageWidth: Float,
    val viewport: ViewportMetrics,
    val pageSpacingPx: Float
) {
    val isEmpty: Boolean get() = pageSizes.isEmpty() || !viewport.isReady

    fun pageHeightPx(index: Int): Float = pageHeights.getOrNull(index) ?: viewport.height

    fun pageWidthPx(index: Int): Float = pageWidths.getOrNull(index) ?: viewport.width

    fun pageTopDocY(index: Int): Float = pageTops.getOrNull(index) ?: 0f

    fun visiblePageIndices(panY: Float, zoom: Float): IntRange {
        if (isEmpty || zoom <= 0f) return IntRange.EMPTY

        val margin = pageSpacingPx * 0.5f
        val docTop = (-panY / zoom) - margin
        val docBottom = ((viewport.height - panY) / zoom) + margin

        val first = firstIntersectingPage(docTop)
        val last = lastIntersectingPage(docBottom)
        return if (first == -1 || last == -1 || first > last) IntRange.EMPTY else first..last
    }

    fun isPointOverPage(screenX: Float, screenY: Float, panX: Float, panY: Float, zoom: Float): Boolean {
        if (isEmpty || zoom <= 0f) return false

        val docY = (screenY - panY) / zoom
        val pageIndex = pageIndexAtDocumentY(docY)
        if (pageIndex == -1) return false
        if (docY > pageTopDocY(pageIndex) + pageHeightPx(pageIndex)) return false

        val pageLeft = pageScreenLeft(pageIndex, panX, zoom)
        return screenX in pageLeft..(pageLeft + pageWidthPx(pageIndex) * zoom)
    }

    fun pageScreenLeft(pageIndex: Int, panX: Float, zoom: Float): Float {
        val pageWidth = pageWidthPx(pageIndex)
        return panX + (maxPageWidth - pageWidth) * zoom / 2f
    }

    fun clampPan(panX: Float, panY: Float, zoom: Float): PanPosition {
        if (!viewport.isReady) return PanPosition(panX, panY)

        val scaledWidth = maxPageWidth * zoom
        val scaledHeight = totalDocumentHeight * zoom

        val clampedX = if (scaledWidth <= viewport.width) {
            (viewport.width - scaledWidth) / 2f
        } else {
            panX.coerceIn(-(scaledWidth - viewport.width), 0f)
        }

        val clampedY = if (scaledHeight <= viewport.height) {
            (viewport.height - scaledHeight) / 2f
        } else {
            panY.coerceIn(viewport.height - scaledHeight, 0f)
        }

        return PanPosition(clampedX, clampedY)
    }

    fun centeredPanForPage(pageIndex: Int, zoom: Float): PanPosition {
        val safeIndex = pageIndex.coerceIn(0, (pageSizes.size - 1).coerceAtLeast(0))
        val pageHeight = pageHeightPx(safeIndex)
        val pageTop = pageTopDocY(safeIndex)

        val centeredPanY = (viewport.height / 2f) - (pageTop + pageHeight / 2f) * zoom
        val centeredPanX = (viewport.width / 2f) - (maxPageWidth * zoom / 2f)
        return PanPosition(centeredPanX, centeredPanY)
    }

    fun fitDocumentZoom(fitMode: FitMode, minZoom: Float, maxZoom: Float): Float {
        if (isEmpty || totalDocumentHeight <= 0f || maxPageWidth <= 0f) return minZoom

        val zoom = when (fitMode) {
            FitMode.WIDTH, FitMode.PROPORTIONAL -> viewport.width / maxPageWidth
            FitMode.HEIGHT -> viewport.height / totalDocumentHeight
            FitMode.BOTH -> min(viewport.width / maxPageWidth, viewport.height / totalDocumentHeight)
        }
        return zoom.coerceIn(minZoom, maxZoom)
    }

    fun fitPageZoom(pageIndex: Int, fitMode: FitMode, minZoom: Float, maxZoom: Float): Float {
        if (isEmpty) return minZoom

        val safeIndex = pageIndex.coerceIn(0, pageSizes.lastIndex)
        val baseWidth = pageWidthPx(safeIndex).takeIf { it > 0f } ?: return minZoom
        val baseHeight = pageHeightPx(safeIndex).takeIf { it > 0f } ?: return minZoom

        val zoom = when (fitMode) {
            FitMode.WIDTH, FitMode.PROPORTIONAL -> viewport.width / baseWidth
            FitMode.HEIGHT -> viewport.height / baseHeight
            FitMode.BOTH -> min(viewport.width / baseWidth, viewport.height / baseHeight)
        }
        return zoom.coerceIn(minZoom, maxZoom)
    }

    fun currentPageAtViewportCenter(panY: Float, zoom: Float): Int? {
        if (isEmpty || zoom <= 0f) return null

        val centerDocY = (viewport.height / 2f - panY) / zoom
        val pageIndex = pageIndexAtDocumentY(centerDocY)
        return pageIndex.takeIf { it >= 0 }?.coerceIn(0, pageTops.lastIndex)
    }

    private fun firstIntersectingPage(docTop: Float): Int {
        var low = 0
        var high = pageTops.lastIndex
        var first = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] + pageHeights[mid] >= docTop) {
                first = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return first
    }

    private fun lastIntersectingPage(docBottom: Float): Int {
        var low = 0
        var high = pageTops.lastIndex
        var last = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] <= docBottom) {
                last = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return last
    }

    private fun pageIndexAtDocumentY(documentY: Float): Int {
        var low = 0
        var high = pageTops.lastIndex
        var pageIndex = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] <= documentY) {
                pageIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return pageIndex
    }

    companion object {
        fun empty(viewport: ViewportMetrics = ViewportMetrics()): PageLayoutSnapshot = PageLayoutSnapshot(
            pageSizes = emptyList(),
            pageTops = FloatArray(0),
            pageHeights = FloatArray(0),
            pageWidths = FloatArray(0),
            totalDocumentHeight = 0f,
            maxPageWidth = viewport.width,
            viewport = viewport,
            pageSpacingPx = 0f
        )

        fun build(
            pageSizes: List<Size>,
            viewportWidth: Float,
            viewportHeight: Float,
            fitMode: FitMode,
            pageSpacingPx: Float
        ): PageLayoutSnapshot {
            val viewport = ViewportMetrics(viewportWidth, viewportHeight)
            if (pageSizes.isEmpty() || !viewport.isReady) return empty(viewport)

            val count = pageSizes.size
            val pageTops = FloatArray(count)
            val pageHeights = FloatArray(count)
            val pageWidths = FloatArray(count)
            val maxPdfWidth = pageSizes.maxOfOrNull { it.width }?.toFloat() ?: 1f
            var documentY = 0f
            var maxPageWidth = 0f

            for (index in 0 until count) {
                val pageSize = pageSizes[index]
                val pdfWidth = pageSize.width.toFloat()
                val pdfHeight = pageSize.height.toFloat()
                val aspectRatio = pdfWidth / pdfHeight

                val (baseWidth, baseHeight) = when (fitMode) {
                    FitMode.WIDTH -> viewport.width to (viewport.width / aspectRatio)
                    FitMode.HEIGHT -> (viewport.height * aspectRatio) to viewport.height
                    FitMode.BOTH -> {
                        val scale = min(viewport.width / pdfWidth, viewport.height / pdfHeight)
                        (pdfWidth * scale) to (pdfHeight * scale)
                    }
                    FitMode.PROPORTIONAL -> {
                        val scale = viewport.width / maxPdfWidth
                        (pdfWidth * scale) to (pdfHeight * scale)
                    }
                }

                pageWidths[index] = baseWidth
                pageHeights[index] = baseHeight
                pageTops[index] = documentY
                documentY += baseHeight + pageSpacingPx
                maxPageWidth = maxOf(maxPageWidth, baseWidth)
            }

            return PageLayoutSnapshot(
                pageSizes = pageSizes,
                pageTops = pageTops,
                pageHeights = pageHeights,
                pageWidths = pageWidths,
                totalDocumentHeight = (documentY - pageSpacingPx).coerceAtLeast(0f),
                maxPageWidth = maxPageWidth,
                viewport = viewport,
                pageSpacingPx = pageSpacingPx
            )
        }
    }
}

/** Viewport metrics used by layout and tile planning. */
internal data class ViewportMetrics(
    val width: Float = 0f,
    val height: Float = 0f
) {
    val isReady: Boolean get() = width > 0f && height > 0f
}

/** Simple value object for pan coordinates. */
internal data class PanPosition(
    val x: Float,
    val y: Float
)
