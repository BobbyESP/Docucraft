/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.composepdf.PdfRenderers
import java.io.File

/**
 * What stands in for the scanner in the screenshots: a sheet in a viewfinder, its edges found.
 *
 * The scanner itself is Google Play services' own screen, in front of a camera: it cannot be shown
 * from here, and nothing about it could be the same from one run to the next. This says what it
 * does without pretending to be it, so it carries no text and none of its controls' labels. The
 * sheet is a page of the sample library, so it is in the language of the screenshot.
 */
@Composable
internal fun ScannerStandIn(page: Bitmap) {
    Box(modifier = Modifier.fillMaxSize().background(Viewfinder)) {
        Box(
            modifier =
                Modifier.align(Alignment.Center)
                    .padding(bottom = 96.dp)
                    .fillMaxWidth(0.74f)
                    .aspectRatio(page.width.toFloat() / page.height)
                    .rotate(-3f)
        ) {
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            // The edges the scanner found, and the corners it lets the user move.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                drawRect(color = Brand.Paper, style = Stroke(width = stroke))
                val corners =
                    listOf(
                        Offset.Zero,
                        Offset(size.width, 0f),
                        Offset(size.width, size.height),
                        Offset(0f, size.height),
                    )
                for (corner in corners) {
                    drawCircle(color = Brand.Paper, radius = 9.dp.toPx(), center = corner)
                    drawCircle(color = Viewfinder, radius = 5.dp.toPx(), center = corner)
                }
            }
        }

        Row(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp)
                    .fillMaxWidth(0.74f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, Brand.Paper, RoundedCornerShape(10.dp)),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(80.dp).border(4.dp, Brand.Paper, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.size(62.dp).background(Brand.Paper, CircleShape))
                }
            }
            Box(modifier = Modifier.size(48.dp).border(2.dp, Brand.Line, CircleShape))
        }
    }
}

/** The first page of the document at [file], on white, [width] pixels wide. */
internal fun firstPageOf(context: Context, file: File, width: Int): Bitmap =
    PdfRenderers.use(
        context,
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
    ) { renderer ->
        renderer.openPage(0).use { page ->
            val bitmap =
                Bitmap.createBitmap(
                    width,
                    width * page.height / page.width,
                    Bitmap.Config.ARGB_8888,
                )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

/** Darker than Ink: what a camera sees of a desk. */
private val Viewfinder = Color(0xFF1B1A17)
