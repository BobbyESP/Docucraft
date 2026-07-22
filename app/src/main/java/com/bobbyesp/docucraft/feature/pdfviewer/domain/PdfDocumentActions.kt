/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.print.PrintManager
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.bobbyesp.docucraft.App
import java.io.File

private const val PDF_MIME_TYPE = "application/pdf"

/**
 * Document-level actions offered by the PDF viewer top bar: share, open in another app, and print.
 *
 * The viewer can be handed either an internal document (a `file://` URI / raw path pointing at a
 * scanned PDF) or an external one (a `content://` URI granted by another app). [shareableUri] bridges
 * the two: `file://` sources are re-exposed through the app [FileProvider] so they can legally cross a
 * process boundary, while `content://` sources are forwarded as-is.
 *
 * [context] must be (or wrap) the host Activity — [print] hands work to [PrintManager], which can only
 * print from an activity. Build this from the composition's `LocalContext`.
 */
class PdfDocumentActions(private val context: Context) {

    /** Launches the system share sheet ([Intent.ACTION_SEND]) for the document. */
    fun share(uriString: String) {
        val uri = shareableUri(uriString) ?: return
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = PDF_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    /** Opens the document in another PDF-capable app via an [Intent.ACTION_VIEW] chooser. */
    fun openWith(uriString: String) {
        val uri = shareableUri(uriString) ?: return
        val viewIntent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, PDF_MIME_TYPE)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

        context.startActivity(Intent.createChooser(viewIntent, null))
    }

    /** Hands the document to the Android print framework. */
    fun print(uriString: String, jobName: String) {
        // PrintManager can only print from an Activity — unwrap the context to find it.
        val activity = context.findActivity() ?: return
        // Printing reads the source directly, so the original (possibly file://) URI is fine here.
        val uri = uriString.toUri()
        val printManager = activity.getSystemService<PrintManager>() ?: return
        val adapter = PdfPrintDocumentAdapter(activity, uri, jobName)
        printManager.print(jobName, adapter, null)
    }

    /**
     * Returns a URI that can safely be granted to another process. `content://` URIs are returned
     * unchanged; anything file-based is wrapped through the app [FileProvider]. Returns `null` if the
     * file cannot be exposed (e.g. it lives outside the configured provider paths).
     */
    private fun shareableUri(uriString: String): Uri? {
        val uri = uriString.toUri()
        return when (uri.scheme) {
            "content" -> uri
            else ->
                runCatching {
                        val file = File(uri.path ?: uriString)
                        FileProvider.getUriForFile(context, App.getAuthority(context), file)
                    }
                    .getOrNull()
        }
    }
}

/** Unwraps [ContextWrapper]s until an [Activity] is found, or `null` if the chain has none. */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
