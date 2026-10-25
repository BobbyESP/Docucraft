/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.library.data.maintenance

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import com.bobbyesp.docucraft.feature.docscanner.data.maintenance.LibraryMaintenanceWorker
import com.bobbyesp.docucraft.library.data.work.WorkManagerGateway
import com.bobbyesp.docucraft.library.domain.maintenance.LibraryMaintenance
import com.bobbyesp.docucraft.library.domain.usecase.PurgeExpiredBinUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ReconcileStorageUseCase
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
