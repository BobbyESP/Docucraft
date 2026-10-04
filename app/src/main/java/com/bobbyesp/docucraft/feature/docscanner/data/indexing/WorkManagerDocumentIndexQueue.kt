/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.indexing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bobbyesp.docucraft.feature.docscanner.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.IndexDocumentTextUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Reads documents with WorkManager, which is what lets the reading outlive the screen that asked
 * for it and the process itself: a document saved a moment before the app is closed is still read.
 *
 * One piece of work per document, named after it. A document already waiting or being read keeps
 * the work it has: that work reads whatever is pending when it runs.
 */
class WorkManagerDocumentIndexQueue(private val context: Context) : DocumentIndexQueue {

    override fun enqueue(documentUuid: String) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                workName(documentUuid),
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IndexDocumentWorker>()
                    .setInputData(workDataOf(IndexDocumentWorker.KEY_DOCUMENT_UUID to documentUuid))
                    .build(),
            )
    }

    internal companion object {
        fun workName(documentUuid: String) = "index/$documentUuid"
    }
}

/**
 * Reads one document. What is read, and what a page that cannot be read means, is the use case's;
 * this only gives it somewhere to run.
 *
 * It ends well whatever the document turned out to be. Work that failed would be kept as failed
 * under the document's name; what is left to read is found again from the pages when the app
 * starts, which does not depend on what WorkManager remembers.
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
