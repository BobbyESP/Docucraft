/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RestoreState {
    data object Idle : RestoreState

    data object Loading : RestoreState

    data object Success : RestoreState

    data class Error(val message: String) : RestoreState
}

class SubscriptionViewModel(private val subscriptionRepository: SubscriptionRepository) :
    ViewModel() {

    val isPro: StateFlow<Boolean> = subscriptionRepository.isPro

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    fun restorePurchases() {
        viewModelScope.launch {
            _restoreState.value = RestoreState.Loading
            subscriptionRepository
                .restorePurchases()
                .onSuccess { _restoreState.value = RestoreState.Success }
                .onFailure { error ->
                    _restoreState.value = RestoreState.Error(error.message ?: "Unknown error")
                }
        }
    }

    fun resetRestoreState() {
        _restoreState.update { RestoreState.Idle }
    }
}
