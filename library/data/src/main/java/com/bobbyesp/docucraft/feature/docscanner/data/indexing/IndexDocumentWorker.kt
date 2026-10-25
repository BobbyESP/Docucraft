/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.indexing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bobbyesp.docucraft.library.domain.usecase.IndexDocumentTextUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Reads one document. What is read, and what a page that cannot be read means, is the use case's;
 * this only gives it somewhere to run.
 *
 * It ends well whatever the document turned out to be. Work that failed would be kept as failed
 * under the document's name; what is left to read is found again from the pages when the app
 * starts, which does not depend on what WorkManager remembers.
 *
 * WorkManager stores this class's name with every queued run, and fails a run it cannot find by it.
 * It keeps its name and package, which are not this module's, like the upkeep's worker.
 */
class IndexDocumentWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {

    private val indexDocumentText: IndexDocumentTextUseCase by inject()

    override suspend fun doWork(): Result {
        val documentUuid = inputData.getString(KEY_DOCUMENT_UUID) ?: return Result.success()
        indexDocumentText(documentUuid)
        return Result.success()
    }

    internal companion object {
        const val KEY_DOCUMENT_UUID = "document_uuid"
    }
}
