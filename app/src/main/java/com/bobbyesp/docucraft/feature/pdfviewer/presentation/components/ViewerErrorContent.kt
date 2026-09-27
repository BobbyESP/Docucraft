/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.composepdf.PdfLoadException

/**
 * Why a document did not open, in the reader's terms. The engine tells the causes apart
 * ([PdfLoadException.Reason]); this decides what to say about each and what is worth offering.
 */
enum class ViewerLoadError(
    @StringRes val title: Int,
    @StringRes val message: Int,
    /**
     * Whether another app might manage where this one did not. Not for a file that is gone or that
     * this app may not read: it cannot hand over what it cannot reach.
     */
    val worthOpeningElsewhere: Boolean,
) {
    NotFound(R.string.viewer_error_not_found, R.string.viewer_error_not_found_desc, false),
    AccessDenied(
        R.string.viewer_error_access_denied,
        R.string.viewer_error_access_denied_desc,
        false,
    ),
    PasswordProtected(R.string.viewer_error_password, R.string.viewer_error_password_desc, true),
    Damaged(R.string.viewer_error_damaged, R.string.viewer_error_damaged_desc, true),
    Unknown(R.string.viewer_error_unknown, R.string.viewer_error_unknown_desc, true);

    companion object {
        fun of(error: Throwable): ViewerLoadError =
            when ((error as? PdfLoadException)?.reason) {
                PdfLoadException.Reason.NOT_FOUND -> NotFound
                PdfLoadException.Reason.ACCESS_DENIED -> AccessDenied
                PdfLoadException.Reason.PASSWORD_PROTECTED -> PasswordProtected
                PdfLoadException.Reason.DAMAGED -> Damaged
                PdfLoadException.Reason.UNKNOWN,
                null -> Unknown
            }
    }
}

/**
 * Shown in place of the pages when the document cannot be loaded: what happened and what the reader
 * can do about it, never the exception's name, which is what used to be shown.
 *
 * @param onOpenWith Hands the document to another app, or `null` when it cannot leave this one.
 * @param onBack Leaves the viewer, or `null` when the viewer shows no way back.
 */
@Composable
fun ViewerErrorContent(
    error: ViewerLoadError,
    onOpenWith: (() -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = 480.dp).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = error.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(error.title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(error.message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        val openWith = onOpenWith?.takeIf { error.worthOpeningElsewhere }
        if (openWith != null || onBack != null) {
            Spacer(Modifier.height(24.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The way forward is the filled button when there is one; otherwise Back is.
                if (openWith != null) {
                    Button(onClick = openWith) {
                        Text(stringResource(R.string.viewer_open_elsewhere))
                    }
                }
                if (onBack != null) {
                    if (openWith != null) {
                        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                    } else {
                        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
                    }
                }
            }
        }
    }
}

private val ViewerLoadError.icon: ImageVector
    get() =
        when (this) {
            ViewerLoadError.NotFound -> Icons.Rounded.SearchOff
            ViewerLoadError.AccessDenied -> Icons.Rounded.Block
            ViewerLoadError.PasswordProtected -> Icons.Rounded.Lock
            ViewerLoadError.Damaged -> Icons.Rounded.BrokenImage
            ViewerLoadError.Unknown -> Icons.Rounded.ErrorOutline
        }
