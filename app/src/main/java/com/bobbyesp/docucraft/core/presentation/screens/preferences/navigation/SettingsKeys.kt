/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The settings area, as flat siblings. Nesting the sub-screens inside `Settings` read as a
 * hierarchy the navigation does not have: the back stack is a list. Restoration is by reflection
 * over the class names; see `proguard-rules.pro` before moving them.
 */
@Serializable data object Settings : NavKey

/** Theme, colour and typography. */
@Serializable data object AppearanceSettings : NavKey

/** RevenueCat's subscription management screen. */
@Serializable data object SubscriptionSettings : NavKey
