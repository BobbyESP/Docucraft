/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface SubscriptionRepository {
    /**
     * StateFlow emitting whether the user currently has the premium ("Docucraft Pro") entitlement active.
     */
    val isPro: StateFlow<Boolean>

    /**
     * Force checks entitlements and updates the local isPro state.
     * Returns true if the user is Pro, false otherwise.
     */
    suspend fun checkEntitlements(): Boolean

    /**
     * Restores purchases made with the current Google Play account.
     */
    suspend fun restorePurchases(): Result<Unit>
}
