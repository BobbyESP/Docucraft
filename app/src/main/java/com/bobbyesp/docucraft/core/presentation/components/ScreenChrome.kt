/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.presentation.theme.frosted
import dev.chrisbanes.haze.HazeState

/*
 * What every screen with a scrolling list has around it: Home, a folder, the bin, settings. Sharing
 * them is what makes those screens read as one app.
 */

/**
 * The expressive large app bar of a screen whose content scrolls. It takes a container tone once
 * the content scrolls beneath it, eased rather than switched, and is frosted in it: what passes
 * under it stays in view, blurred. Until then nothing is beneath it, and it is the page's own
 * surface.
 *
 * @param hazeState Where the content that scrolls beneath it is recorded.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FrostedLargeTopAppBar(
    title: String,
    isContentScrolled: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scrolledFraction by
        animateFloatAsState(
            targetValue = if (isContentScrolled) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "TopBarScrolled",
        )
    val containerColor =
        lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceContainer,
            scrolledFraction,
        )

    LargeFlexibleTopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle =
            subtitle?.let {
                { Text(text = it, maxLines = 1, overflow = TextOverflow.StartEllipsis) }
            },
        modifier =
            modifier.frosted(
                state = hazeState,
                style = DocucraftBlurDefaults.surfaceStyle(containerColor),
            ),
        navigationIcon = navigationIcon,
        actions = actions,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * The name of a section of a list, with room at its end for what acts on the whole section.
 *
 * @param leading What goes before the name, such as the dot of a tag.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 20.dp, end = if (trailing != null) 8.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title.uppercase(),
            style =
                MaterialTheme.typography.labelLargeEmphasized.copy(
                    letterSpacing = 1.25.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                ),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}
