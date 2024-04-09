package com.bobbyesp.docucraft.presentation.components.card

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.domain.model.SavedPdf
import com.bobbyesp.docucraft.presentation.theme.DocucraftTheme
import com.bobbyesp.ui.components.layouts.lazygrid.GridMenuItem
import com.bobbyesp.ui.motion.MotionConstants.DURATION_ENTER
import com.bobbyesp.ui.motion.MotionConstants.DURATION_EXIT_SHORT
import com.bobbyesp.ui.motion.MotionConstants.EmphasizedAccelerateEasing
import com.bobbyesp.ui.motion.MotionConstants.EmphasizedDecelerateEasing
import com.bobbyesp.ui.motion.MotionConstants.boundsTransformation
import com.bobbyesp.utilities.Time
import com.bobbyesp.utilities.parseFileSize

@Composable
fun SavedPdfFileCard(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    pdf: SavedPdf,
    onShareRequest: (SavedPdf) -> Unit = {},
    onOpenPdf: (SavedPdf) -> Unit = {}
) {
    val fileSize: String? by remember {
        mutableStateOf(pdf.fileSizeBytes?.let { parseFileSize(it) })
    }
    val creationTime by remember {
        mutableStateOf(Time.Localized.getFormattedDate(pdf.savedTimestamp))
    }

    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = contentModifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(6.dp),
                    imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    contentDescription = stringResource(id = R.string.file_icon),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = pdf.title ?: pdf.fileName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        modifier = Modifier,
                        text = pdf.description ?: stringResource(id = R.string.no_description),
                        fontWeight = FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetadataInfo(
                    modifier = Modifier, icon = Icons.Rounded.CalendarMonth, text = stringResource(
                        id = R.string.created_at
                    ) + " " + creationTime
                )

//                pdf.path?.let {
//                    MetadataInfo(
//                        modifier = Modifier, icon = Icons.Rounded.Place, text = it.toString()
//                    )
//                }

                MetadataInfo(
                    modifier = Modifier,
                    icon = Icons.Rounded.AccountTree,
                    text = pdf.pageCount.toString() + " " + stringResource(id = R.string.pages)
                )

                fileSize?.let {
                    MetadataInfo(
                        modifier = Modifier,
                        icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                        text = stringResource(id = R.string.size) + ": " + it
                    )
                }

            }
            HorizontalDivider()
            LazyVerticalGrid(
                modifier = Modifier.fillMaxWidth(), columns = GridCells.Adaptive(minSize = 120.dp)
            ) {
                GridMenuItem(icon = { Icons.Rounded.FileOpen },
                    title = { stringResource(id = R.string.open_file) }) {
                    onOpenPdf(pdf)
                }
                GridMenuItem(icon = { Icons.Rounded.Share },
                    title = { stringResource(id = R.string.share) }) {
                    onShareRequest(pdf)
                }
            }
        }
    }
}

context(SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SavedPdfCardTransitionsWrapper(
    modifier: Modifier = Modifier,
    pdf: SavedPdf?,
    onShareRequest: (SavedPdf) -> Unit = {},
    onOpenPdf: (SavedPdf) -> Unit,
    onDismissRequest: () -> Unit
) {
    AnimatedContent(
        modifier = Modifier,
        targetState = pdf, label = "",
    ) { savedPdf ->
        if (savedPdf == null) return@AnimatedContent
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            SavedPdfFileCard(
                modifier = Modifier.sharedBounds(
                    boundsTransform = boundsTransformation,
                    enter = fadeIn(
                        tween(
                            durationMillis = DURATION_ENTER,
                            delayMillis = DURATION_EXIT_SHORT,
                            easing = EmphasizedDecelerateEasing
                        )
                    ),
                    exit = fadeOut(
                        tween(
                            durationMillis = DURATION_EXIT_SHORT,
                            easing = EmphasizedAccelerateEasing
                        )
                    ),
                    sharedContentState = rememberSharedContentState(key = "${savedPdf.savedTimestamp}_bounds"),
                    animatedVisibilityScope = this@AnimatedContent,
                    placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
                ),
                contentModifier = modifier,
                pdf = savedPdf,
                onShareRequest = onShareRequest,
                onOpenPdf = onOpenPdf
            )
        }
    }
}

@Composable
private fun MetadataInfo(modifier: Modifier = Modifier, icon: ImageVector, text: String) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = icon,
            contentDescription = stringResource(
                id = R.string.metadata_icon
            ),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CardPrev() {
    DocucraftTheme {
        SavedPdfFileCard(
            pdf = SavedPdf.emptyPdf(
                title = "This is a test file", fileSizeBytes = 56345745
            )
        )
    }
}