package com.bobbyesp.docucraft.core.util

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith

val emphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)

val emphasizedTransform: ContentTransform = (fadeIn(animationSpec = tween(500, easing = emphasizedEasing)) +
        scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(500, easing = emphasizedEasing)
        ))
    .togetherWith(
        fadeOut(
            animationSpec = tween(
                200,
                easing = emphasizedEasing
            )
        )
    )