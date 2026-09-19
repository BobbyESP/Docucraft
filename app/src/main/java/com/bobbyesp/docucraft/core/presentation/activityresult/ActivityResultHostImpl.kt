/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.activityresult

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-scoped [ActivityResultHost] that the current Activity lends itself to.
 *
 * The Activity calls [attach] once it has registered a launcher, [deliver] when a result arrives,
 * and [detach] on the way out. Callers that arrive while no Activity is attached — a request made
 * during a configuration change, say — suspend until one is, instead of failing.
 */
class ActivityResultHostImpl : ActivityResultHost {

    private class Binding(
        val activity: Activity,
        val launcher: ActivityResultLauncher<IntentSenderRequest>,
    )

    private val binding = MutableStateFlow<Binding?>(null)

    /** Results are correlated by arrival order, so only one request may be out at a time. */
    private val oneAtATime = Mutex()
    private val pending = AtomicReference<CompletableDeferred<ActivityResult>?>(null)

    fun attach(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        binding.value = Binding(activity, launcher)
    }

    /**
     * Ignores Activities other than the attached one, so a recreation cannot unbind its successor.
     */
    fun detach(activity: Activity) {
        if (binding.value?.activity === activity) binding.value = null
    }

    fun deliver(result: ActivityResult) {
        pending.getAndSet(null)?.complete(result)
    }

    override suspend fun <T> withActivity(block: suspend (Activity) -> T): T =
        block(awaitBinding().activity)

    override suspend fun launchIntentSender(request: IntentSenderRequest): ActivityResult =
        oneAtATime.withLock {
            val launcher = awaitBinding().launcher
            val result = CompletableDeferred<ActivityResult>()
            pending.set(result)

            try {
                withContext(Dispatchers.Main.immediate) { launcher.launch(request) }
                result.await()
            } finally {
                pending.compareAndSet(result, null)
            }
        }

    private suspend fun awaitBinding(): Binding = binding.filterNotNull().first()
}
