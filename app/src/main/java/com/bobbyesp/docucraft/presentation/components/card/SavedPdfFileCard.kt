package com.bobbyesp.docucraft.presentation.components.card

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.InsertDriveFile
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.domain.model.SavedPdf
import com.bobbyesp.docucraft.presentation.theme.DocucraftTheme
import com.bobbyesp.utilities.Time
import com.bobbyesp.utilities.parseFileSize

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedPdfFileCard(
    modifier: Modifier = Modifier,
    pdf: SavedPdf,
) {
    val context = LocalContext.current
    val fileSize: String? by remember {
        mutableStateOf(pdf.fileSizeBytes?.let { parseFileSize(it) })
    }
    val creationTime by remember {
        mutableStateOf(Time.Localized.getFormattedDate(pdf.savedTimestamp))
    }

    Surface(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetadataInfo(modifier = Modifier, icon = Icons.Rounded.CalendarMonth, text = stringResource(
                    id = R.string.created_at
                ) + " " + creationTime)

                fileSize?.let {
                    MetadataInfo(modifier = Modifier, icon = Icons.AutoMirrored.Rounded.InsertDriveFile, text = stringResource(id = R.string.size) + ": " + it)
                }

            }
            HorizontalDivider()
            LazyVerticalGrid(modifier = Modifier.fillMaxWidth(), columns = GridCells.Adaptive(minSize = 120.dp)) {
                //TODO: Add items
            }
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
            modifier = Modifier.size(28.dp),
            imageVector = icon, contentDescription = stringResource(
            id = R.string.metadata_icon
        ))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CardPrev() {
    DocucraftTheme {
        SavedPdfFileCard(pdf = SavedPdf.emptyPdf(title = "This is a test file", fileSizeBytes = 56345745))
    }
}