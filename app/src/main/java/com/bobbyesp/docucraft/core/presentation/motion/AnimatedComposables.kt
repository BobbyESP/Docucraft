package com.bobbyesp.docucraft.core.presentation.motion

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import com.bobbyesp.docucraft.core.presentation.motion.MaterialEasing.EmphasizedAccelerate
import com.bobbyesp.docucraft.core.presentation.motion.MaterialEasing.EmphasizedDecelerate
import com.bobbyesp.docucraft.core.presentation.motion.MotionConstants.DURATION_ENTER
import com.bobbyesp.docucraft.core.presentation.motion.MotionConstants.DURATION_EXIT
import com.bobbyesp.docucraft.core.presentation.motion.MotionConstants.InitialOffset
import kotlin.reflect.KType

private val fadeSpring =
    spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

private val _fadeEnterSpec = tween<Float>(durationMillis = DURATION_ENTER)
private val _fadeExitSpec = tween<Float>(durationMillis = DURATION_EXIT)

val fadeEnterSpec = _fadeEnterSpec
val fadeExitSpec = _fadeExitSpec

inline fun <reified T : Any> NavGraphBuilder.animatedComposable(
    deepLinks: List<NavDeepLink> = emptyList(),
    usePredictiveBack: Boolean = Build.VERSION.SDK_INT >= 34,
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) {
    if (usePredictiveBack) {
        animatedComposablePredictiveBack<T>(deepLinks, content)
    } else {
        animatedComposableLegacy<T>(deepLinks, content)
    }
}

inline fun <reified T : Any> NavGraphBuilder.slideInVerticallyComposable(
    deepLinks: List<NavDeepLink> = emptyList(),
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    usePredictiveBack: Boolean = Build.VERSION.SDK_INT >= 34,
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) {
    if (usePredictiveBack) {
        slideInVerticallyComposablePredictiveBack<T>(deepLinks, typeMap, content)
    } else {
        slideInVerticallyComposableLegacy<T>(deepLinks, typeMap, content)
    }
}

inline fun <reified T : Any> NavGraphBuilder.animatedComposablePredictiveBack(
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) =
    composable<T>(
        deepLinks = deepLinks,
        enterTransition = { materialSharedAxisXIn(initialOffsetX = { (it * 0.15f).toInt() }) },
        exitTransition = {
            materialSharedAxisXOut(targetOffsetX = { -(it * InitialOffset).toInt() })
        },
        popEnterTransition = {
            scaleIn(
                animationSpec =
                    tween(durationMillis = DURATION_ENTER, easing = EmphasizedDecelerate),
                initialScale = 0.9f,
            ) + materialSharedAxisXIn(initialOffsetX = { -(it * InitialOffset).toInt() })
        },
        popExitTransition = {
            materialSharedAxisXOut(targetOffsetX = { (it * InitialOffset).toInt() }) +
                scaleOut(
                    targetScale = 0.9f,
                    animationSpec =
                        tween(durationMillis = DURATION_EXIT, easing = EmphasizedAccelerate),
                )
        },
        content = content,
    )

inline fun <reified T : Any> NavGraphBuilder.animatedComposableLegacy(
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) =
    composable<T>(
        deepLinks = deepLinks,
        enterTransition = {
            materialSharedAxisXIn(initialOffsetX = { (it * InitialOffset).toInt() })
        },
        exitTransition = {
            materialSharedAxisXOut(targetOffsetX = { -(it * InitialOffset).toInt() })
        },
        popEnterTransition = {
            materialSharedAxisXIn(initialOffsetX = { -(it * InitialOffset).toInt() })
        },
        popExitTransition = {
            materialSharedAxisXOut(targetOffsetX = { (it * InitialOffset).toInt() })
        },
        content = content,
    )

inline fun <reified T : Any> NavGraphBuilder.animatedComposableVariant(
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) =
    composable<T>(
        deepLinks = deepLinks,
        enterTransition = {
            slideInHorizontally(tweenEnter(), initialOffsetX = { (it * InitialOffset).toInt() }) +
                fadeIn(fadeEnterSpec)
        },
        exitTransition = { fadeOut(fadeExitSpec) },
        popEnterTransition = { fadeIn(fadeEnterSpec) },
        popExitTransition = {
            slideOutHorizontally(tweenExit(), targetOffsetX = { (it * InitialOffset).toInt() }) +
                fadeOut(fadeExitSpec)
        },
        content = content,
    )

val springSpec =
    spring(stiffness = Spring.StiffnessMedium, visibilityThreshold = IntOffset.VisibilityThreshold)

inline fun <reified T : Any> NavGraphBuilder.slideInVerticallyComposableLegacy(
    deepLinks: List<NavDeepLink> = emptyList(),
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) =
    composable<T>(
        deepLinks = deepLinks,
        typeMap = typeMap,
        enterTransition = {
            slideInVertically(initialOffsetY = { it }, animationSpec = tweenEnter()) + fadeIn()
        },
        exitTransition = { slideOutVertically() },
        popEnterTransition = { slideInVertically() },
        popExitTransition = {
            slideOutVertically(targetOffsetY = { it }, animationSpec = tweenExit()) + fadeOut()
        },
        content = content,
    )

inline fun <reified T : Any> NavGraphBuilder.slideInVerticallyComposablePredictiveBack(
    deepLinks: List<NavDeepLink> = emptyList(),
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) =
    composable<T>(
        deepLinks = deepLinks,
        typeMap = typeMap,
        enterTransition = { materialSharedAxisYIn(initialOffsetY = { (it * 0.25f).toInt() }) },
        exitTransition = {
            materialSharedAxisYOut(targetOffsetY = { -(it * InitialOffset * 1.5f).toInt() })
        },
        popEnterTransition = {
            scaleIn(
                animationSpec =
                    tween(durationMillis = DURATION_ENTER, easing = EmphasizedDecelerate),
                initialScale = 0.85f,
            ) + materialSharedAxisYIn(initialOffsetY = { -(it * InitialOffset * 1.5f).toInt() })
        },
        popExitTransition = {
            materialSharedAxisYOut(targetOffsetY = { (it * InitialOffset * 1.5f).toInt() }) +
                scaleOut(
                    targetScale = 0.85f,
                    animationSpec =
                        tween(durationMillis = DURATION_EXIT, easing = EmphasizedAccelerate),
                )
        },
        content = content,
    )
