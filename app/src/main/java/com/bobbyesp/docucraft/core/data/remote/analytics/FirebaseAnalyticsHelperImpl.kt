package com.bobbyesp.docucraft.core.data.remote.analytics

import android.util.Log
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

class FirebaseAnalyticsHelperImpl(
    private val firebaseAnalytics: FirebaseAnalytics
): AnalyticsHelper {
    override fun logEvent(event: AnalyticsEvent) {
        Log.d("Analytics", "Logging event: ${event.type}, extras: ${event.extras}")
        firebaseAnalytics.logEvent(event.type) {
            event.extras.forEach { extraData ->
                param(
                    key = extraData.key.take(40),
                    value = extraData.value.take(100),
                )
            }
        }
    }
}