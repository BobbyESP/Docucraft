/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Default values and factories for [PdfViewer].
 *
 * Style defaults are theme-aware: [style] reads the current [MaterialTheme] so the viewer blends
 * into light and dark hosts without configuration.
 */
object PdfViewerDefaults {

    /** Default spacing between consecutive pages. */
    val PageSpacing: Dp = 8.dp

    /** Default base-page oversampling factor. See [PdfRenderSpec.quality]. */
    const val RenderQuality: Float = 1.5f

    /** Default minimum committed zoom. */
    const val MinZoom: Float = 1f

    /** Default maximum committed zoom. */
    const val MaxZoom: Float = 8f

    /** Default zoom level reached by a double tap. See [PdfZoomSpec.doubleTapZoom]. */
    const val DoubleTapZoom: Float = 2.5f

    /** Default page prefetch distance. See [PdfRenderSpec.prefetchDistance]. */
    const val PrefetchDistance: Int = 2

    /**
     * Creates a [PdfViewerStyle] whose colors follow the current Material theme: a subdued
     * container in light themes, a dark one in dark themes.
     */
    @Composable
    fun style(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
        pageColor: Color = Color.White,
        pageCornerRadius: Dp = 0.dp,
        pageShadowColor: Color = Color(0x33000000),
        nightMode: Boolean = false,
        scrollIndicator: PdfScrollIndicatorStyle? = scrollIndicator(),
        showPageLoadingIndicator: Boolean = true,
    ): PdfViewerStyle =
        PdfViewerStyle(
            containerColor = containerColor,
            pageColor = pageColor,
            pageCornerRadius = pageCornerRadius,
            pageShadowColor = pageShadowColor,
            nightMode = nightMode,
            scrollIndicator = scrollIndicator,
            showPageLoadingIndicator = showPageLoadingIndicator,
        )

    /** Creates a [PdfScrollIndicatorStyle] tinted from the current Material theme. */
    @Composable
    fun scrollIndicator(
        color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        thickness: Dp = 4.dp,
        padding: Dp = 4.dp,
        minLength: Dp = 48.dp,
    ): PdfScrollIndicatorStyle =
        PdfScrollIndicatorStyle(
            color = color,
            thickness = thickness,
            padding = padding,
            minLength = minLength,
        )

    /** Default content shown while the document loads. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun LoadingContent(modifier: Modifier = Modifier) {
        CircularWavyProgressIndicator(
            modifier = modifier.size(56.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }

    /** Default content shown when the document fails to load. */
    @Composable
    fun ErrorContent(error: Throwable, modifier: Modifier = Modifier) {
        Column(
            modifier = modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = error::class.simpleName ?: "Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = error.message ?: "The document could not be loaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

    /** Default indicator centered on pages that have not rendered yet. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun PageLoadingIndicator(modifier: Modifier = Modifier) {
        CircularWavyProgressIndicator(
            modifier = modifier.size(40.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
