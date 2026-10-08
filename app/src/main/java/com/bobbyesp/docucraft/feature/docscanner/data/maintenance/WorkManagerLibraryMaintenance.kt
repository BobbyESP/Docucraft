/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.maintenance

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.bobbyesp.docucraft.feature.docscanner.data.work.WorkManagerGateway
import com.bobbyesp.docucraft.feature.docscanner.domain.maintenance.LibraryMaintenance
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.PurgeExpiredBinUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ReconcileStorageUseCase
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Runs the library's upkeep once a day with WorkManager, which keeps the turn across restarts of
 * the app and of the device. The bin's 30 days are therefore kept to within a day, which is as
 * exact as a bin needs to be.
 */
class WorkManagerLibraryMaintenance(
    private val workManager: WorkManagerGateway,
    private val purgeExpiredBin: () -> PurgeExpiredBinUseCase,
    private val reconcileStorage: () -> ReconcileStorageUseCase,
    private val fallbackScope: CoroutineScope,
) : LibraryMaintenance {

    override fun schedule() {
        val scheduled =
            runCatching {
                workManager
                    .get()
                    ?.enqueueUniquePeriodicWork(
                        WORK_NAME,
                        // What is scheduled keeps its turn: replacing it on every start of the
                        // app would push the next run a day further each time.
                        ExistingPeriodicWorkPolicy.KEEP,
                        PeriodicWorkRequestBuilder<LibraryMaintenanceWorker>(1, TimeUnit.DAYS)
                            .setConstraints(
                                Constraints.Builder().setRequiresBatteryNotLow(true).build()
                            )
                            .build(),
                    )
            }
                .getOrNull() != null
        if (!scheduled) runInApp()
    }

    /**
     * Without WorkManager nothing keeps the turn across restarts, so the upkeep runs each time the
     * app starts instead: it only does what is due, so doing it more often costs nothing but time.
     */
    private fun runInApp() {
        fallbackScope.launch(Dispatchers.Default) {
            runCatching { purgeExpiredBin()() }
            runCatching { reconcileStorage()() }
        }
    }

    internal companion object {
        const val WORK_NAME = "library-maintenance"
    }
}

/**
 * One run of the upkeep. What is purged and what is reconciled are the use cases'; this only gives
 * them somewhere to run.
 *
 * Each part runs whether or not the other failed, and the work ends well either way: it runs again
 * tomorrow, and neither part depends on what the last run got done.
 */
class LibraryMaintenanceWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {

    private val purgeExpiredBin: PurgeExpiredBinUseCase by inject()
    private val reconcileStorage: ReconcileStorageUseCase by inject()

    override suspend fun doWork(): Result {
        runCatching { purgeExpiredBin() }
        runCatching { reconcileStorage() }
        return Result.success()
    }
}
