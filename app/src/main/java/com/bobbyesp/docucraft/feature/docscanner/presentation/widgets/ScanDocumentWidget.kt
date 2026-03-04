package com.bobbyesp.docucraft.feature.docscanner.presentation.widgets

import android.content.Context
import android.content.Intent
import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bobbyesp.docucraft.MainActivity
import com.bobbyesp.docucraft.R

const val ACTION_SCAN_DOCUMENT = "com.bobbyesp.docucraft.ACTION_SCAN_DOCUMENT"

private val COMPACT_HEIGHT_THRESHOLD = 120.dp

private val COMPACT = DpSize(110.dp, 40.dp)
private val EXPANDED = DpSize(110.dp, 180.dp)

class ScanDocumentWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(COMPACT, EXPANDED)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val scanTitle = context.getString(R.string.widget_scan_title)
        val scanSubtitle = context.getString(R.string.widget_scan_subtitle)
        val appName = context.getString(R.string.widget_app_name)
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                @Suppress("GlanceComposable")
                if (size.height < COMPACT_HEIGHT_THRESHOLD) {
                    ScanDocumentWidgetCompact(scanTitle = scanTitle)
                } else {
                    ScanDocumentWidgetContent(
                        appName = appName,
                        scanTitle = scanTitle,
                        scanSubtitle = scanSubtitle,
                    )
                }
            }
        }
    }
}

@SuppressLint("ModifierParameter")
@Composable
@GlanceComposable
internal fun ScanDocumentWidgetCompact(scanTitle: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.primary)
            .cornerRadius(28.dp)
            .clickable(actionRunCallback<ScanDocumentActionCallback>())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(R.drawable.rounded_document_scanner_24),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = scanTitle,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@SuppressLint("ModifierParameter")
@Composable
@GlanceComposable
internal fun ScanDocumentWidgetContent(
    appName: String,
    scanTitle: String,
    scanSubtitle: String,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(28.dp)
            .clickable(actionRunCallback<ScanDocumentActionCallback>()),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Vertical.Top,
            horizontalAlignment = Alignment.Horizontal.Start,
        ) {
            // ── Branding row ─────────────────────────────────────────────
            Row(
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = appName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Normal,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // ── Hero: logo de la app ──────────────────────────────────────
            Box(
                modifier = GlanceModifier
                    .size(52.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_foreground_splashscreen),
                    contentDescription = null,
                    modifier = GlanceModifier.size(48.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // ── Title — M3 "Title Large" scale ───────────────────────────
            Text(
                text = scanTitle,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            // ── Subtitle — M3 "Body Small" scale ────────────────────────
            Text(
                text = scanSubtitle,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )

            Spacer(modifier = GlanceModifier.height(14.dp))

            // ── Pill action button ───────────────────────────────────────
            Box(
                modifier = GlanceModifier
                    .wrapContentWidth()
                    .height(34.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(50.dp)
                    .padding(horizontal = 16.dp, vertical = 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = GlanceModifier.wrapContentWidth().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_scan_camera),
                        contentDescription = null,
                        modifier = GlanceModifier.size(15.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = scanTitle,
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

class ScanDocumentActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_SCAN_DOCUMENT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        context.startActivity(intent)
    }
}
