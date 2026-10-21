/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.maintenance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bobbyesp.docucraft.library.domain.usecase.PurgeExpiredBinUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ReconcileStorageUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * One run of the upkeep. What is purged and what is reconciled are the use cases'; this only gives
 * them somewhere to run.
 *
 * Each part runs whether or not the other failed, and the work ends well either way: it runs again
 * tomorrow, and neither part depends on what the last run got done.
 *
 * WorkManager stores this class's name with the scheduled work, and runs nothing it cannot find by
 * it: the work is kept on its schedule (`KEEP`), so a new name would leave the upkeep failing every
 * day. It keeps its name and package, which are not this module's.
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
