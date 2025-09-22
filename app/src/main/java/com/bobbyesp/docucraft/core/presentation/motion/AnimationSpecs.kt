package com.bobbyesp.docucraft.core.presentation.motion

import android.view.animation.PathInterpolator
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import com.bobbyesp.docucraft.core.presentation.motion.MaterialEasing.Emphasized
import com.bobbyesp.docucraft.core.presentation.motion.MaterialEasing.EmphasizedAccelerateEasing
import com.bobbyesp.docucraft.core.presentation.motion.MaterialEasing.EmphasizedDecelerateEasing
import com.bobbyesp.docucraft.core.presentation.motion.MotionConstants.DURATION
import com.bobbyesp.docucraft.core.presentation.motion.MotionConstants.DURATION_ENTER
import com.bobbyesp.docucraft.core.presentation.motion.MotionConstants.DURATION_EXIT
import com.bobbyesp.docucraft.core.presentation.motion.MotionConstants.DURATION_EXIT_SHORT
import com.bobbyesp.docucraft.core.presentation.motion.MotionConstants.InitialOffset

fun PathInterpolator.toEasing(): Easing {
    return Easing { f -> this.getInterpolation(f) }
}

fun <T> tweenEnter(delayMillis: Int = DURATION_EXIT, durationMillis: Int = DURATION_ENTER) =
    tween<T>(
        delayMillis = delayMillis,
        durationMillis = durationMillis,
        easing = EmphasizedDecelerateEasing,
    )

fun <T> tweenExit(delayMillis: Int = DURATION_EXIT_SHORT, durationMillis: Int = DURATION_EXIT) =
    tween<T>(
        delayMillis = delayMillis,
        durationMillis = durationMillis,
        easing = EmphasizedAccelerateEasing,
    )

@OptIn(ExperimentalSharedTransitionApi::class)
val DefaultBoundsTransform = BoundsTransform { _, _ ->
    tween(easing = Emphasized, durationMillis = DURATION)
}

val DefaultContentTransform: ContentTransform =
    ContentTransform(
        targetContentEnter = materialSharedAxisXIn(initialOffsetX = { (it * 0.15f).toInt() }),
        initialContentExit =
            materialSharedAxisXOut(targetOffsetX = { -(it * InitialOffset).toInt() }),
    )
