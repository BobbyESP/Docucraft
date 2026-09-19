/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.scanner.mlkit

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import java.util.concurrent.atomic.AtomicBoolean
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
 *
 * ### Surviving the process
 *
 * Whatever was launched runs in another process, so the system is free to kill this one while it is
 * in the background. `registerForActivityResult` handles its half: the registry stores pending
 * requests in the Activity's saved state and re-delivers once the Activity is recreated. What it
 * cannot do is find the coroutine that was waiting, because that died with the process.
 *
 * So a result that arrives with nobody waiting is **buffered** rather than dropped, and whoever
 * comes back looking for it calls [awaitPendingResult]. [hasPendingLaunch] is what tells a caller
 * apart from one asking about a session that never existed; the Activity is expected to carry it
 * through its own saved state, which is the same mechanism that preserves the registry's pending
 * request. The two therefore survive, or fail to, together.
 */
class ActivityResultHostImpl : ActivityResultHost {

    private class Binding(
        val activity: Activity,
        val launcher: ActivityResultLauncher<IntentSenderRequest>,
    )

    private val binding = MutableStateFlow<Binding?>(null)

    /** Results are correlated by arrival order, so only one request may be out at a time. */
    private val oneAtATime = Mutex()
    private val waiting = AtomicReference<CompletableDeferred<ActivityResult>?>(null)

    /** A result that arrived with nobody waiting for it. */
    private val orphaned = AtomicReference<ActivityResult?>(null)

    private val launchOutstanding = AtomicBoolean(false)

    /**
     * Whether a launch is still unanswered. The Activity should persist this across its own
     * recreation; see [restorePendingLaunch].
     */
    val hasPendingLaunch: Boolean
        get() = launchOutstanding.get() || orphaned.get() != null

    fun attach(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        binding.value = Binding(activity, launcher)
    }

    /**
     * Ignores Activities other than the attached one, so a recreation cannot unbind its successor.
     */
    fun detach(activity: Activity) {
        if (binding.value?.activity === activity) binding.value = null
    }

    /**
     * Tells a rebuilt host that a launch was outstanding when the process went away.
     *
     * Ignored once a result is already in hand: the registry re-delivers during the Activity's
     * `onCreate`, which can happen before the Activity gets around to restoring this.
     */
    fun restorePendingLaunch(pending: Boolean) {
        if (pending && orphaned.get() == null) launchOutstanding.set(true)
    }

    fun deliver(result: ActivityResult) {
        launchOutstanding.set(false)

        val caller = waiting.getAndSet(null)
        if (caller != null) caller.complete(result) else orphaned.set(result)
    }

    override suspend fun <T> withActivity(block: suspend (Activity) -> T): T =
        block(awaitBinding().activity)

    override suspend fun launchIntentSender(request: IntentSenderRequest): ActivityResult =
        oneAtATime.withLock {
            val launcher = awaitBinding().launcher

            // Anything left over belongs to a session nobody claimed; it must not be mistaken for
            // the answer to this one.
            orphaned.set(null)

            val result = CompletableDeferred<ActivityResult>()
            waiting.set(result)
            launchOutstanding.set(true)

            try {
                withContext(Dispatchers.Main.immediate) { launcher.launch(request) }
                result.await()
            } finally {
                waiting.compareAndSet(result, null)
            }
        }

    override suspend fun awaitPendingResult(): ActivityResult? {
        orphaned.getAndSet(null)?.let {
            return it
        }
        if (!launchOutstanding.get()) return null

        return oneAtATime.withLock {
            // Both may have changed while queueing for the lock.
            orphaned.getAndSet(null)?.let {
                return@withLock it
            }
            if (!launchOutstanding.get()) return@withLock null

            val result = CompletableDeferred<ActivityResult>()
            waiting.set(result)

            try {
                result.await()
            } finally {
                waiting.compareAndSet(result, null)
            }
        }
    }

    private suspend fun awaitBinding(): Binding = binding.filterNotNull().first()
}
