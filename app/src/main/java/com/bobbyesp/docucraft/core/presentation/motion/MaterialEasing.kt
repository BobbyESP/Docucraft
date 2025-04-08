package com.bobbyesp.docucraft.core.presentation.motion

import android.graphics.Path
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.CubicBezierEasing

private val path = Path().apply {
    moveTo(0f, 0f)
    cubicTo(0.05F, 0F, 0.133333F, 0.06F, 0.166666F, 0.4F)
    cubicTo(0.208333F, 0.82F, 0.25F, 1F, 1F, 1F)
}

object MaterialEasing {
    val EmphasizedPathInterpolator = PathInterpolator(path)
    val Emphasized = EmphasizedPathInterpolator.toEasing()
    val EmphasizedVariant = CubicBezierEasing(.2f, 0f, 0f, 1f)

    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    val StandardDecelerate = CubicBezierEasing(.0f, .0f, 0f, 1f)
    val Standard = CubicBezierEasing(0.4F, 0.0F, 0.2F, 1F)
}