/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.di

import com.bobbyesp.docucraft.core.data.remote.subscription.SubscriptionRepositoryImpl
import com.bobbyesp.docucraft.core.domain.repository.SubscriptionRepository
import com.bobbyesp.docucraft.core.presentation.screens.preferences.subscription.SubscriptionViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val subscriptionModule = module {
    single<SubscriptionRepository> {
        SubscriptionRepositoryImpl(
            appScope = get(qualifier = named("AppMainSupervisedScope"))
        )
    }

    viewModelOf(::SubscriptionViewModel)
}
