package com.bobbyesp.docucraft.feature.pdfscanner.presentation.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import com.bobbyesp.docucraft.MainActivity
import com.bobbyesp.docucraft.R

const val ACTION_SCAN_PDF = "com.bobbyesp.docucraft.ACTION_SCAN_PDF"

class ScanPdfDocumentWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val scanText = context.getString(R.string.scan_new_document)
        provideContent { ScanPdfWidgetContent(scanButtonText = scanText) }
    }
}

@GlanceComposable
@Composable
fun ScanPdfWidgetContent(scanButtonText: String) {
    GlanceTheme {
        Row(
            modifier =
                GlanceModifier.fillMaxSize()
                    .background(GlanceTheme.colors.background)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                text = scanButtonText,
                onClick = actionRunCallback<ScanPdfActionCallback>(),
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(GlanceTheme.colors.primary)
                        .padding(16.dp),
            )
        }
    }
}

class ScanPdfActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_SCAN_PDF
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        context.startActivity(intent)
    }
}


