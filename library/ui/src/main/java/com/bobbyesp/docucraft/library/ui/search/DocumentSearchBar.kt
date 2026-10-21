/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.library.ui.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.designsystem.theme.frosted
import com.bobbyesp.docucraft.core.navigation.motion.sharedBoundsAcrossDestinations
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.rememberHazeState

/**
 * Both ends of the search bar's morph: Home's button and the search screen's field. One key and one
 * shape, so the container that travels between them is recognisably the same one.
 */
object SearchBarElement {
    const val KEY = "document-search-bar"
    val Shape = CircleShape
    val Height = 56.dp

    /** Frosted on both ends too, so the container that travels does not change material halfway. */
    @Composable
    @ReadOnlyComposable
    fun frostedStyle(): HazeBlurStyle =
        DocucraftBlurDefaults.surfaceStyle(MaterialTheme.colorScheme.surfaceContainerHigh)
}

/**
 * Home's way into search: looks like the search bar it becomes, and is not one. Typing happens on
 * the search screen, which this grows into.
 *
 * Kept at the bottom beside the scan button, where the thumb already is, and floating over the list
 * like it. Frosted rather than shadowed: the documents scrolling beneath show through it, blurred.
 * What lifts it is the blur halo its caller draws around it and the scan button together; where
 * there is none, the shadow it used to have.
 *
 * @param hazeState Where the content it floats over is recorded.
 */
@Composable
fun DocumentSearchBarButton(
    onClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .sharedBoundsAcrossDestinations(SearchBarElement.KEY, SearchBarElement.Shape)
                .height(SearchBarElement.Height)
                .frosted(
                    state = hazeState,
                    style = SearchBarElement.frostedStyle(),
                    shape = SearchBarElement.Shape,
                ),
        shape = SearchBarElement.Shape,
        color = Color.Transparent,
        shadowElevation = if (DocucraftBlurDefaults.isHaloSupported) 0.dp else 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(DesignsystemR.string.search_documents),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DocumentSearchBarButtonPreview() {
    DocucraftTheme {
        DocumentSearchBarButton(
            onClick = {},
            hazeState = rememberHazeState(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
