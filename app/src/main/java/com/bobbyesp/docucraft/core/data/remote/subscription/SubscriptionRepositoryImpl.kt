/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.data.remote.subscription

import android.util.Log
import com.bobbyesp.docucraft.core.domain.repository.SubscriptionRepository
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubscriptionRepositoryImpl(
    appScope: CoroutineScope
) : SubscriptionRepository {

    private val _isPro: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    companion object {
        private const val ENTITLEMENT_PRO = "Docucraft Pro"
        private const val TAG = "SubscriptionRepository"
    }

    init {
        try {
            // Listen to reactive CustomerInfo updates (e.g. successful purchases, restores, etc.)
            Purchases.sharedInstance.updatedCustomerInfoListener =
                UpdatedCustomerInfoListener { customerInfo ->
                    Log.d(TAG, "CustomerInfo updated in listener")
                    updateProStatus(customerInfo)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set CustomerInfo listener: ${e.message}")
        }

        // Fetch current customer info on app launch
        appScope.launch {
            try {
                val customerInfo = Purchases.sharedInstance.awaitCustomerInfo()
                updateProStatus(customerInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch initial CustomerInfo: ${e.message}")
            }
        }
    }

    override suspend fun checkEntitlements(): Boolean {
        return try {
            val customerInfo = Purchases.sharedInstance.awaitCustomerInfo()
            updateProStatus(customerInfo)
            _isPro.value
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check entitlements: ${e.message}")
            _isPro.value
        }
    }

    override suspend fun restorePurchases(): Result<Unit> {
        return try {
            val customerInfo = Purchases.sharedInstance.awaitRestore()
            updateProStatus(customerInfo)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore purchases: ${e.message}")
            Result.failure(e)
        }
    }

    private fun updateProStatus(customerInfo: CustomerInfo) {
        val isActive = customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true
        Log.d(TAG, "Updating $ENTITLEMENT_PRO status: $isActive")
        _isPro.update { isActive }
    }
}
