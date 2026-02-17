package com.bobbyesp.docucraft.core.presentation.utilities

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope

object AnimatableSaver : Saver<Animatable<Float, AnimationVector1D>, Float> {
    override fun restore(value: Float): Animatable<Float, AnimationVector1D> {
        return Animatable(value)
    }

    override fun SaverScope.save(value: Animatable<Float, AnimationVector1D>): Float {
        return value.value
    }
}
