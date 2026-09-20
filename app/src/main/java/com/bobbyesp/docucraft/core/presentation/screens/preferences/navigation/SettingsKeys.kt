/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The settings area.
 *
 * Flat siblings rather than a nesting. The two sub-screens used to be declared inside the
 * `Settings` object, which read as a hierarchy but was only a naming device: the back stack is a
 * list, and neither of them is reachable except from [Settings] anyway. Naming them in full says
 * the same thing without implying structure the navigation does not have.
 *
 * Restoration is by reflection over these class names — see `proguard-rules.pro` before moving
 * them.
 */
@Serializable data object Settings : NavKey

/** Theme, colour and typography. */
@Serializable data object AppearanceSettings : NavKey

/** RevenueCat's subscription management screen. */
@Serializable data object SubscriptionSettings : NavKey
