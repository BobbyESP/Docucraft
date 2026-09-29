/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurDefaults
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3

/**
 * Where Docucraft blurs, and how much. Material has no blur tokens, so these stand in for elevation
 * where a surface floats over content that moves beneath it: frosting what is behind the surface
 * tells the two apart better than a shadow, and keeps the content's colors in view. The surface
 * keeps its Material color role, only translucent.
 *
 * Three kinds of blur, for three jobs:
 * - **Frosted surfaces** ([surfaceStyle] and [frosted], on Haze): a bar, a search field or a menu,
 *   over content recorded with `Modifier.hazeSource`.
 * - **Halos** ([blurHalo], on Haze): the content around a floating element out of focus, where a
 *   shadow would darken it.
 * - **Content out of focus** (`Modifier.blur` with a `BlurRadiusSpec`): the screen behind a sheet
 *   or a dialog, or a thumbnail behind its title.
 *
 * Below Android 12 neither blurs: a frosted surface falls back to its container color, nearly
 * opaque, and content that would go out of focus stays sharp. Both look as they did before blur.
 */
object DocucraftBlurDefaults {

    /**
     * Behind a frosted surface: enough that text beneath cannot be read, not so much that its
     * colors turn to mud.
     */
    val SurfaceRadius: Dp = 24.dp

    /**
     * The screen behind a sheet or a dialog: out of focus but still recognizable, so it is clear
     * what the overlay was opened from.
     */
    val BehindOverlayRadius: Dp = 12.dp

    /** A thumbnail under its title, at the bottom edge, where the text sits. */
    val BehindTitleRadius: Dp = 16.dp

    /**
     * How far a [blurHalo] reaches past its element. Wider than the shadow it replaces: a shadow
     * shows over any background, a blur only over detail, so it needs the room to be seen.
     */
    val HaloSpread: Dp = 48.dp

    /**
     * A menu's halo: wider than [HaloSpread], because a menu opens over more of the content, and
     * its halo is all that separates the two.
     */
    val MenuHaloSpread: Dp = 56.dp

    /** The halo's blur at the element's edge, easing to none at the end of its spread. */
    val HaloRadius: Dp = 24.dp

    /**
     * How much further below its element the halo reaches than above it, as a fraction of its
     * spread: Material's key light casts its shadows downwards, and the halo keeps that direction.
     */
    private const val HaloDropFraction = 0.25f

    /** How far down a halo of [spread] is moved; see [HaloDropFraction]. */
    fun haloDrop(spread: Dp): Dp = spread * HaloDropFraction

    /** The room a halo of [spread] needs on every side of its element. */
    fun haloMargin(spread: Dp = HaloSpread): Dp = spread + haloDrop(spread)

    /**
     * A breath of the surface color over the halo's blur, fading with it: over a flat area, where
     * blur alone changes nothing, the element still has a faint rim around it. Kept low, or the rim
     * reads as a band with an edge of its own.
     */
    internal const val HaloVeilOpacity = 0.18f

    /** The halo's grain: the same reason as [NoiseFactor], over a narrower blur. */
    internal const val HaloNoiseFactor = 0.04f

    /**
     * Whether [blurHalo] draws: its radius varies with distance, which Haze does from Android 13.
     * Where it does not, an element keeps the shadow the halo replaces.
     */
    val isHaloSupported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            HazeBlurDefaults.isBlurEnabledByDefault()

    /**
     * How much of the container color covers the blur. Enough for the content colors on it to keep
     * their contrast whatever scrolls beneath; a dark container needs more, because light content
     * shows through it more.
     */
    private const val LightContainerOpacity = 0.76f
    private const val DarkContainerOpacity = 0.82f

    /**
     * A little grain, which keeps a wide blur of flat colors from banding. Less than Haze's
     * default: Material surfaces are flat, and more reads as texture.
     */
    private const val NoiseFactor = 0.06f

    /**
     * A frosted [containerColor]: what is behind the surface, blurred, under the container color at
     * [LightContainerOpacity] or [DarkContainerOpacity]. Pass the role the surface would have used
     * as a solid color, so the frosted one sits at the same place in the color scheme.
     */
    @Composable
    @ReadOnlyComposable
    fun surfaceStyle(containerColor: Color): HazeBlurStyle =
        HazeBlurStyle.Material3(containerColor) {
            blurRadius(SurfaceRadius)
            noiseFactor(NoiseFactor)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(
                        containerColor.copy(
                            alpha =
                                if (containerColor.luminance() >= 0.5f) LightContainerOpacity
                                else DarkContainerOpacity
                        )
                    )
                )
            )
        }
}

/**
 * Frosts this surface with [style], blurring what [state] records beneath it. The surface's own
 * container must then be transparent, and its shadow gone: the frost is what sets it apart.
 *
 * [HazeInput.Sources] rather than the backdrop, because it also reaches across windows: a menu is a
 * popup of its own, and still frosts the list it opened over. Clipped to [shape], the surface's
 * own, because the blur is drawn to the rectangle of the layout.
 */
fun Modifier.frosted(
    state: HazeState,
    style: HazeBlurStyle,
    shape: Shape = RectangleShape,
): Modifier = clip(shape).hazeBlur(input = HazeInput.Sources(state), style = style)
