package com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults

@Composable
fun DocumentActionSheetSkeleton(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = DocucraftShapeDefaults.topListItemShape
        ) {
            header.invoke()
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = DocucraftShapeDefaults.bottomListItemShape
        ) {
            content.invoke()
        }

        footer.invoke()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentActionSheetSkeleton(
    headingTitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    headingDescription: String? = null,
    footer: @Composable () -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    val iconSize: Dp = 28.dp

    DocumentActionSheetSkeleton(
        modifier = modifier,
        header = {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .clip(MaterialShapes.Cookie6Sided.toShape())
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .size(iconSize * 2)
                            .padding(iconSize / 2)
                            .size(iconSize)
                    )
                }

                Column(
                    modifier = Modifier,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = headingTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    headingDescription?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        footer = footer,
        content = content
    )
}