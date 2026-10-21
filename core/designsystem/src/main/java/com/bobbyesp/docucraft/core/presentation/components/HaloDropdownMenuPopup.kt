/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.presentation.theme.blurHalo
import dev.chrisbanes.haze.HazeState

/**
 * Material's expressive dropdown menu, lifted off what it opens over by a blur halo instead of a
 * shadow: the content around the menu goes out of focus, most at its edge, and is sharp again a
 * little further out. The groups inside should drop their own shadow where the halo is supported
 * ([DocucraftBlurDefaults.isHaloSupported]).
 *
 * A popup's window ends where its content does, so the halo has to take room inside it. That room
 * is made up for in two places:
 * - the menu is still placed where Material would place it, as if the room were not there;
 * - a tap on the halo closes the menu, as a tap anywhere outside it would.
 *
 * @param hazeState Where the content the menu opens over is recorded.
 * @param shape The outline of the menu as a whole, which the halo follows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HaloDropdownMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    hazeState: HazeState,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spread = DocucraftBlurDefaults.MenuHaloSpread
    val margin =
        if (DocucraftBlurDefaults.isHaloSupported) DocucraftBlurDefaults.haloMargin(spread)
        else 0.dp
    val marginPx = with(LocalDensity.current) { margin.roundToPx() }
    val menuPosition =
        MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below)
    val positionProvider =
        remember(menuPosition, marginPx) { HaloMarginPositionProvider(menuPosition, marginPx) }
    val dismiss by rememberUpdatedState(onDismissRequest)

    // The halo comes into focus on its own, not inside Material's open animation: in there it was
    // scaled by the menu's spring, overshoot included, and cut to the menu's bounds while the menu
    // faded. The scheme's effects specs never overshoot; on the way in, the default one, so the
    // halo settles just after the menu lands rather than jumping with it.
    val haloStrength = remember { Animatable(0f) }
    val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(expanded) {
        if (expanded) {
            haloStrength.snapTo(0f)
            haloStrength.animateTo(1f, enterSpec)
        } else {
            haloStrength.animateTo(0f, exitSpec)
        }
    }

    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        // Material applies this outside its own scale and fade, which only the menu then goes
        // through.
        modifier =
            modifier
                .pointerInput(marginPx) {
                    detectTapGestures { tap ->
                        val menu =
                            IntRect(
                                left = marginPx,
                                top = marginPx,
                                right = size.width - marginPx,
                                bottom = size.height - marginPx,
                            )
                        if (!menu.contains(IntOffset(tap.x.toInt(), tap.y.toInt()))) dismiss()
                    }
                }
                .blurHalo(
                    state = hazeState,
                    shape = shape,
                    spread = spread,
                    strength = haloStrength.value,
                    reserveSpace = true,
                ),
        popupPositionProvider = positionProvider,
        properties = HaloMenuProperties,
        content = content,
    )
}

/**
 * Places the menu where [menu] would, as if the popup were only the menu, then moves the popup out
 * by [margin] so the halo around it lands around it.
 */
private class HaloMarginPositionProvider(
    private val menu: DropdownMenuPopupPositionProvider,
    private val margin: Int,
) : DropdownMenuPopupPositionProvider {

    override val transformOrigin
        get() = menu.transformOrigin

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val menuSize =
            IntSize(
                (popupContentSize.width - 2 * margin).coerceAtLeast(0),
                (popupContentSize.height - 2 * margin).coerceAtLeast(0),
            )
        val position = menu.calculatePosition(anchorBounds, windowSize, layoutDirection, menuSize)
        return IntOffset(position.x - margin, position.y - margin)
    }
}

/**
 * Material's menu properties, unclipped: the menu itself is kept on screen by the position above,
 * and a window kept on screen as a whole would push the menu off its place to fit a halo that may
 * run past the screen's edge.
 */
private val HaloMenuProperties =
    PopupProperties(
        focusable = true,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        clippingEnabled = false,
        usePlatformDefaultWidth = false,
    )
