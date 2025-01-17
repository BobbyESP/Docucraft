package com.bobbyesp.ui.components.layouts.lazygrid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.ui.R

val GridMenuItemHeight = 96.dp

fun LazyGridScope.GridMenuItem(
    modifier: Modifier = Modifier,
    icon: @Composable () -> ImageVector,
    title: @Composable () -> String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    item {
        Surface(
            modifier = modifier
                .clip(ShapeDefaults.Large)
                .height(GridMenuItemHeight)
                .alpha(if (enabled) 1f else 0.5f)
                .padding(12.dp),
            shape = MaterialTheme.shapes.small,
            onClick = onClick,
            enabled = enabled,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = icon(),
                    contentDescription = stringResource(id = R.string.list_item_icon)
                )
                Text(
                    text = title(),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

//fun LazyGridScope.GridMenuItem(
//    modifier: Modifier = Modifier,
//    icon: ImageVector,
//    title: String,
//    enabled: Boolean = true,
//    onClick: () -> Unit,
//) {
//    GridMenuItem(
//        modifier = modifier,
//        icon = { icon },
//        title = { title },
//        enabled = enabled,
//        onClick = onClick
//    )
//}
//
//fun LazyGridScope.GridMenuItem(
//    modifier: Modifier = Modifier,
//    icon: ImageVector,
//    title: @Composable () -> String,
//    enabled: Boolean = true,
//    onClick: () -> Unit,
//) {
//    GridMenuItem(
//        modifier = modifier,
//        icon = { icon },
//        title = title,
//        enabled = enabled,
//        onClick = onClick
//    )
//}