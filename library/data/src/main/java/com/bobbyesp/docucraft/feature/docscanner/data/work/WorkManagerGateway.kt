/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.work

import android.content.Context
import android.util.Log
import androidx.work.WorkManager

/**
 * The one way the app reaches WorkManager, which may not be there.
 *
 * WorkManager starts the first time it is asked for (the app removes its startup initializer, see
 * the manifest), and starting it can fail on a device whose system does not match the version it
 * reports: some builds say they are Android 14 and lack `JobScheduler.forNamespace`, and
 * WorkManager's own check of the version cannot know that. Started at launch, that failure killed
 * the process before the first screen; started here, it is a [WorkManager] that is not available.
 * Whoever needs one then does its work another way, because the work is background upkeep that the
 * app can do while it is open, and not the reason to open it.
 *
 * A start that failed is not retried: it would fail the same way each time, and slowly.
 */
class WorkManagerGateway(private val context: Context) {

    private val workManager: WorkManager? by lazy {
        try {
            WorkManager.getInstance(context)
        } catch (e: Exception) {
            report(e)
        } catch (e: LinkageError) {
            // A system method missing at run time is not an Exception.
            report(e)
        }
    }

    /** The [WorkManager], or `null` if it could not start on this device. */
    fun get(): WorkManager? = workManager

    private fun report(error: Throwable): WorkManager? {
        Log.w(TAG, "WorkManager is not available on this device; working in the app instead", error)
        return null
    }

    private companion object {
        const val TAG = "WorkManagerGateway"
    }
}
