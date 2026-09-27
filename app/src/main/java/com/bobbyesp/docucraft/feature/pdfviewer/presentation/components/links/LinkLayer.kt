/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.links

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.BlockReason
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkAction
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.LinkPreview
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageLink
import com.composepdf.PdfOverlayScope

/**
 * The links over the pages: the tapped one highlighted with its preview beside it (D3), and a node
 * for each link that TalkBack can reach and open, since links are drawn into the page and are
 * otherwise invisible to it.
 *
 * @param describe What a link is, for TalkBack: where it leads.
 */
@Composable
fun PdfOverlayScope.LinkLayer(
    pageLinks: Map<Int, List<PageLink>>,
    preview: LinkPreview?,
    describe: @Composable (PageLink) -> String,
    onTapLink: (page: Int, link: PageLink) -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    val showLink = stringResource(R.string.link_a11y_action)
    for ((page, links) in pageLinks) {
        for (link in links) {
            val area = link.bounds.reduce(NormalizedRect::union)
            val description = describe(link)
            Box(
                Modifier.coverArea(page, area.toRect()).semantics {
                    contentDescription = description
                    role = Role.Button
                    onClick(label = showLink) {
                        onTapLink(page, link)
                        true
                    }
                }
            )
        }
    }

    if (preview == null) return

    // The tapped link, marked for as long as its preview shows.
    val mark = MaterialTheme.colorScheme.primary.copy(alpha = MarkAlpha)
    DrawOnPages {
        if (pageIndex != preview.page) return@DrawOnPages
        val area = toViewer(preview.area.toRect())
        drawRect(color = mark, topLeft = area.topLeft, size = area.size)
    }

    LinkPreviewCard(
        preview = preview,
        onOpen = onOpen,
        onCopy = onCopy,
        modifier =
            Modifier.anchorTo(
                    pageIndex = preview.page,
                    position =
                        Offset(
                            (preview.area.left + preview.area.right) / 2f,
                            preview.area.bottom,
                        ),
                    alignment = Alignment.TopCenter,
                    stayInside = true,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Where a link really leads, before anything opens (D3): the host a browser would open, in ASCII,
 * with the full address beneath it and a warning for plain `http`; the address of an email; the
 * number of a phone link; or why a link will not be followed.
 */
@Composable
private fun LinkPreviewCard(
    preview: LinkPreview,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = previewContent(preview.action)
    val pane = stringResource(R.string.link_preview_pane)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        // Taps on the card are the card's: they must not reach the page and close it.
        modifier =
            modifier
                .widthIn(max = 360.dp)
                .pointerInput(Unit) { detectTapGestures {} }
                .semantics {
                    paneTitle = pane
                },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = content.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp).semantics { heading() },
                )
            }
            content.detail?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 32.dp, end = 8.dp),
                )
            }
            if (content.warning != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = content.warning,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (preview.copyable != null) {
                    TextButton(onClick = onCopy) { Text(stringResource(R.string.link_copy)) }
                }
                if (preview.canOpen && content.open != null) {
                    Button(onClick = onOpen) { Text(content.open) }
                }
            }
        }
    }
}

private class PreviewContent(
    val icon: ImageVector,
    val title: String,
    val detail: String?,
    val warning: String? = null,
    /** The label of the button that follows the link; `null` when it cannot be followed. */
    val open: String? = null,
)

@Composable
private fun previewContent(action: LinkAction): PreviewContent =
    when (action) {
        is LinkAction.OpenWeb ->
            PreviewContent(
                icon = Icons.Rounded.Language,
                title = action.host,
                detail = action.url,
                warning = if (action.secure) null else stringResource(R.string.link_insecure),
                open = stringResource(R.string.link_open),
            )
        is LinkAction.ComposeEmail ->
            PreviewContent(
                icon = Icons.Rounded.Email,
                title = stringResource(R.string.link_email),
                detail = action.address,
                open = stringResource(R.string.link_email_action),
            )
        is LinkAction.Dial ->
            PreviewContent(
                icon = Icons.Rounded.Call,
                title = stringResource(R.string.link_call),
                detail = action.number,
                open = stringResource(R.string.link_call),
            )
        is LinkAction.Blocked ->
            PreviewContent(
                icon = Icons.Rounded.Block,
                title = stringResource(R.string.link_blocked_title),
                detail =
                    when (action.reason) {
                        BlockReason.Scheme -> stringResource(R.string.link_blocked_scheme)
                        BlockReason.Malformed -> stringResource(R.string.link_blocked_malformed)
                        BlockReason.OutsideDocument -> stringResource(R.string.link_blocked_outside)
                    },
            )
        // Followed at once, never previewed.
        is LinkAction.GoTo -> PreviewContent(Icons.Rounded.Language, "", null)
    }

private fun NormalizedRect.toRect() = Rect(left, top, right, bottom)

private const val MarkAlpha = 0.2f
