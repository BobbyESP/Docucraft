package com.bobbyesp.docucraft.presentation.components.card

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.domain.model.SavedPdf
import com.bobbyesp.docucraft.presentation.theme.DocucraftTheme
import com.bobbyesp.ui.motion.MotionConstants.DURATION_ENTER
import com.bobbyesp.ui.motion.MotionConstants.DURATION_EXIT_SHORT
import com.bobbyesp.ui.motion.MotionConstants.EmphasizedAccelerateEasing
import com.bobbyesp.ui.motion.MotionConstants.EmphasizedDecelerateEasing
import com.bobbyesp.ui.motion.MotionConstants.boundsTransformation
import com.bobbyesp.utilities.parseFileSize

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedPdfFileListItem(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    pdf: SavedPdf,
    onClick: () -> Unit,
    onLongPressed: () -> Unit
) {
    val fileSize: String? by remember {
        mutableStateOf(pdf.fileSizeBytes?.let { parseFileSize(it) })
    }

    Surface(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongPressed
        )
    ) {
        Row(
            modifier = contentModifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier
                    .size(32.dp)
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
                    modifier = Modifier,
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
            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                fileSize?.let {
                    Text(
                        text = stringResource(id = R.string.size) + " " + it,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                    )
                }
            }
        }
    }
}

context(SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SavedPdfListItemTransitionsWrapper(
    modifier: Modifier = Modifier,
    pdf: SavedPdf,
    onClick: () -> Unit,
    onLongPressed: () -> Unit,
    visible: Boolean
) {
    var height by remember { mutableStateOf<Dp?>(null) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .onSizeChanged {
                height = density.run { it.height.toDp() }
            }
    ) {
        // Since the item disappears after the animation has finished, it would disappear from the list. We want to remember how high it was, to reserve teh space
        // Not sure how failsafe this is
        height?.let {
            Spacer(modifier = Modifier.height(it))
        }

        AnimatedVisibility(
            visible = visible
        ) {
            SavedPdfFileListItem(
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
                    sharedContentState = rememberSharedContentState(key = "${pdf.savedTimestamp}_bounds"),
                    animatedVisibilityScope = this@AnimatedVisibility,
                    placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
                ),
                pdf = pdf, onClick = onClick, onLongPressed = onLongPressed)
        }
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CardPrev() {
    DocucraftTheme {
        SavedPdfFileListItem(pdf = SavedPdf.emptyPdf(), onClick = { /*TODO*/ }) {

        }
    }
}