/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.util

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.Modifier

/*
 * Both take the theme's MotionScheme rather than fixed durations, so what they move settles with the
 * same springs as every expressive component around it. Read the scheme in composition
 * (`MaterialTheme.motionScheme`) and pass it in: transition specs and item modifiers are built
 * outside of it.
 */

/**
 * New content taking the place of the old, such as a loaded screen replacing its loading indicator:
 * it grows into place while what it replaces fades quickly out of the way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MotionScheme.contentRevealTransform(): ContentTransform =
    (fadeIn(defaultEffectsSpec()) +
        scaleIn(defaultSpatialSpec(), initialScale = 0.92f)) togetherWith fadeOut(fastEffectsSpec())

/**
 * A lazy list item that fades in and out as it joins or leaves the list, while the items around it
 * glide to their new places instead of jumping.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyItemScope.animateItemWith(motionScheme: MotionScheme): Modifier =
    Modifier.animateItem(
        fadeInSpec = motionScheme.defaultEffectsSpec(),
        placementSpec = motionScheme.defaultSpatialSpec(),
        fadeOutSpec = motionScheme.fastEffectsSpec(),
    )
