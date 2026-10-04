/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.presentation.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.presentation.components.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsCategory
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsGroup
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsItem
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsItemDefaults
import com.bobbyesp.docucraft.core.presentation.components.settings.settingsContentWidth
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.collections.immutable.persistentListOf

/** Where the screen sends the user. None of them is the user's to change, so none is a string. */
private object AboutLinks {
    const val BUY_ME_A_COFFEE = "https://buymeacoffee.com/bobbyesp"
    const val DEVELOPER = "https://bobbyesp.eu"
    const val SOURCE_CODE = "https://github.com/BobbyESP/Docucraft"
}

/**
 * What the app says about itself: which app and version this is, how to support it, and where its
 * author and its code are found.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier, showBackButton: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    val notifications = LocalNotificationsService.current
    val noAppMessage = stringResource(R.string.link_no_app)

    // A device without a browser has nothing to open these with: said, rather than thrown.
    val open = { url: String ->
        runCatching { uriHandler.openUri(url) }
            .onFailure {
                notifications.show(
                    InAppNotification(message = noAppMessage, type = NotificationType.Error)
                )
            }
        Unit
    }

    AboutScreenContent(
        onBack = onBack,
        onSupport = { open(AboutLinks.BUY_ME_A_COFFEE) },
        onOpenDeveloper = { open(AboutLinks.DEVELOPER) },
        onOpenSourceCode = { open(AboutLinks.SOURCE_CODE) },
        modifier = modifier,
        showBackButton = showBackButton,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreenContent(
    onBack: () -> Unit,
    onSupport: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenSourceCode: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    // The list, recorded for the app bar to frost once it scrolls beneath it.
    val hazeState = rememberHazeState()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FrostedLargeTopAppBar(
                title = stringResource(R.string.about),
                isContentScrolled = listState.canScrollBackward,
                scrollBehavior = scrollBehavior,
                hazeState = hazeState,
                navigationIcon = {
                    // Beside the settings list there is already a way back on screen.
                    if (showBackButton) {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            state = listState,
            // Padded rather than inset, so the list scrolls beneath the app bar that frosts it.
            contentPadding =
                PaddingValues(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = paddingValues.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hero", contentType = "about_hero") {
                AppHero(modifier = Modifier.settingsContentWidth().padding(bottom = 16.dp))
            }
            item(key = "support", contentType = "about_support") {
                SupportCard(
                    onSupport = onSupport,
                    modifier =
                        Modifier.settingsContentWidth()
                            .padding(horizontal = SettingsItemDefaults.HorizontalMargin),
                )
            }
            item(key = "links", contentType = "settings_section") {
                SettingsCategory(
                    title = stringResource(R.string.about_links),
                    modifier = Modifier.settingsContentWidth(),
                ) {
                    SettingsGroup(
                        items =
                            persistentListOf(
                                SettingsItem(
                                    // A person's name reads the same in every language.
                                    title = DEVELOPER_NAME,
                                    supportingText = stringResource(R.string.about_developer_role),
                                    icon = Icons.Rounded.Person,
                                    onClick = onOpenDeveloper,
                                ),
                                SettingsItem(
                                    title = stringResource(R.string.about_source_code),
                                    supportingText =
                                        stringResource(R.string.about_source_code_desc),
                                    icon = Icons.Rounded.Code,
                                    onClick = onOpenSourceCode,
                                ),
                            )
                    )
                }
            }
        }
    }
}

private const val DEVELOPER_NAME = "Gabriel Fontán"

/**
 * The app's icon, name and installed version. Nothing here is tapped, so it has no container, only
 * the icon on one of the expressive shapes the app's empty screens carry.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppHero(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The installed package says which version this is; a preview has none to ask.
    val versionName =
        remember(context) {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
                .getOrNull()
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier.padding(bottom = 12.dp)
                    .size(AppIconSize)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            // The launcher's own foreground, in the theme's colors as a themed icon is. Drawn
            // larger than its container, as the launcher does: only the middle two thirds of an
            // adaptive icon's layer are meant to be seen.
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(AppIconSize * AdaptiveIconLayerRatio),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMediumEmphasized,
        )
        if (versionName != null) {
            Text(
                text = stringResource(R.string.version_format, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val AppIconSize = 120.dp

/** An adaptive icon's layer is 108dp, of which a launcher shows 72dp. */
private const val AdaptiveIconLayerRatio = 108f / 72f

/**
 * The one thing the screen asks of the user, so the one thing on it with a color of its own: the
 * tertiary container the app keeps for what it has to say, as the bin's notice is, and a button in
 * its accent rather than a row among rows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SupportCard(onSupport: () -> Unit, modifier: Modifier = Modifier) {
    val buttonHeight = ButtonDefaults.MediumContainerHeight

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DocucraftShapeDefaults.cardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(imageVector = Icons.Rounded.Favorite, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.about_support_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.about_support_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(
                onClick = onSupport,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = buttonHeight),
                // The card's own pair, turned around: whatever the theme makes of the tertiary
                // tones, the button stands out from the card it is on.
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                contentPadding =
                    ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Coffee,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                Text(
                    text = stringResource(R.string.about_buy_me_a_coffee),
                    style = ButtonDefaults.textStyleFor(buttonHeight),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AboutScreenPreview() {
    DocucraftTheme {
        AboutScreenContent(onBack = {}, onSupport = {}, onOpenDeveloper = {}, onOpenSourceCode = {})
    }
}
