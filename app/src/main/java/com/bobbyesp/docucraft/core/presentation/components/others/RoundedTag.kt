package com.bobbyesp.docucraft.core.presentation.components.others

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

/**
 * A compact tag with rounded corners and optional leading icon.
 *
 * This composable creates a customizable tag component with rounded corners. It supports
 * an optional leading icon, customizable colors, shapes, and padding. The tag is styled
 * using Material 3 design principles.
 *
 * @param text The label displayed inside the tag.
 * @param modifier The [Modifier] applied to the outer [Surface].
 * @param icon An optional leading [ImageVector] icon displayed before the text.
 * @param border An optional [BorderStroke] for the tag's border.
 * @param shape The [Shape] of the tag's corners. Defaults to [MaterialTheme.shapes.medium].
 * @param containerColor The background color of the tag. Defaults to [MaterialTheme.colorScheme.secondaryContainer].
 * @param contentColor The color applied to the text and icon. Defaults to a color derived from [containerColor].
 * @param contentPadding The padding inside the tag. Defaults to 8.dp horizontal and 4.dp vertical.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundedTag(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    border: BorderStroke? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge, // Más redondeado para mayor expresividad
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = contentColorFor(containerColor),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp), // Padding más generoso
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = shape,
        border = border
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = contentColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}


@PreviewLightDark
@Composable
private fun RoundedTagPreview() {
    DocucraftTheme() {
        Surface {
            RoundedTag(
                text = "14.53 kB",
                icon = Icons.Rounded.Storage,
            )
        }
    }
}