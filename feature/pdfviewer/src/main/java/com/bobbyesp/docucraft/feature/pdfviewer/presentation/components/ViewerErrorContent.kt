/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfviewer.R
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
    NotFound(
        DesignsystemR.string.viewer_error_not_found,
        R.string.viewer_error_not_found_desc,
        false,
    ),
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ViewerErrorContent(
    error: ViewerLoadError,
    onOpenWith: (() -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.widthIn(max = 600.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A file that is gone or locked is not the app's fault, so only a broken one is red.
            val badgeContainer =
                if (error.isFault) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer
            val badgeContent =
                if (error.isFault) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(96.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(badgeContainer),
            ) {
                Icon(
                    imageVector = error.icon,
                    contentDescription = null,
                    tint = badgeContent,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(error.title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
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
                Spacer(Modifier.height(32.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val height = ButtonDefaults.MediumContainerHeight
                    val padding = ButtonDefaults.contentPaddingFor(height)
                    // The way forward is the filled button when there is one; otherwise Back is.
                    if (openWith != null) {
                        Button(
                            onClick = openWith,
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = padding,
                            modifier = Modifier.heightIn(min = height),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                            )
                            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
                            Text(
                                text = stringResource(R.string.viewer_open_elsewhere),
                                style = ButtonDefaults.textStyleFor(height),
                            )
                        }
                    }
                    if (onBack != null) {
                        val backLabel: @Composable () -> Unit = {
                            Text(
                                text = stringResource(DesignsystemR.string.back),
                                style = ButtonDefaults.textStyleFor(height),
                            )
                        }
                        if (openWith != null) {
                            TextButton(
                                onClick = onBack,
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = padding,
                                modifier = Modifier.heightIn(min = height),
                                content = { backLabel() },
                            )
                        } else {
                            Button(
                                onClick = onBack,
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = padding,
                                modifier = Modifier.heightIn(min = height),
                                content = { backLabel() },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ViewerLoadError.isFault: Boolean
    get() = this == ViewerLoadError.Damaged || this == ViewerLoadError.Unknown

private val ViewerLoadError.icon: ImageVector
    get() =
        when (this) {
            ViewerLoadError.NotFound -> Icons.Rounded.SearchOff
            ViewerLoadError.AccessDenied -> Icons.Rounded.Block
            ViewerLoadError.PasswordProtected -> Icons.Rounded.Lock
            ViewerLoadError.Damaged -> Icons.Rounded.BrokenImage
            ViewerLoadError.Unknown -> Icons.Rounded.ErrorOutline
        }

private class ViewerLoadErrorProvider : PreviewParameterProvider<ViewerLoadError> {
    override val values = ViewerLoadError.entries.asSequence()
}

@PreviewLightDark
@Composable
private fun ViewerErrorContentPreview(
    @PreviewParameter(ViewerLoadErrorProvider::class) error: ViewerLoadError
) {
    DocucraftTheme { ViewerErrorContent(error = error, onOpenWith = {}, onBack = {}) }
}

@PreviewLightDark
@Composable
private fun ViewerErrorContentBackOnlyPreview() {
    DocucraftTheme {
        ViewerErrorContent(error = ViewerLoadError.NotFound, onOpenWith = null, onBack = {})
    }
}
