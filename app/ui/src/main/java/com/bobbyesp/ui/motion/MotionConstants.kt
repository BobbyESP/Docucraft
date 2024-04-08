package com.bobbyesp.ui.motion

/*
 * Copyright 2021 SOUP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import android.view.animation.PathInterpolator
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object MotionConstants {

    // Material 3 Emphasized Easing
    // https://m3.material.io/styles/motion/easing-and-duration/tokens-specs

    const val DURATION = 600
    const val DURATION_ENTER = 400
    const val DURATION_ENTER_SHORT = 300
    const val DURATION_EXIT = 200
    const val DURATION_EXIT_SHORT = 100


    val emphasizedPath = android.graphics.Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    }

    val emphasizedDecelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    val emphasized = PathInterpolator(emphasizedPath)

    val EmphasizedEasing: Easing = Easing { fraction -> emphasized.getInterpolation(fraction) }
    val EmphasizedDecelerateEasing =
        Easing { fraction -> emphasizedDecelerate.getInterpolation(fraction) }
    val EmphasizedAccelerateEasing =
        Easing { fraction -> emphasizedAccelerate.getInterpolation(fraction) }

    const val DefaultMotionDuration: Int = 300
    const val DefaultFadeInDuration: Int = 150
    const val DefaultFadeOutDuration: Int = 75
    val DefaultSlideDistance: Dp = 30.dp

    const val initialOffset = 0.10f
}