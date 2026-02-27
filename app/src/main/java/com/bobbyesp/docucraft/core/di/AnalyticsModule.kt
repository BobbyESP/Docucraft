package com.bobbyesp.docucraft.core.di

import com.bobbyesp.docucraft.core.data.remote.analytics.FirebaseAnalyticsHelperImpl
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.koin.dsl.module

val analyticsModule = module {
    single<FirebaseAnalytics> { Firebase.analytics }
    single<AnalyticsHelper> { FirebaseAnalyticsHelperImpl(firebaseAnalytics = get()) }
}