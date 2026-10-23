/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.library.data.indexing

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.bobbyesp.docucraft.feature.docscanner.data.indexing.IndexDocumentWorker
import com.bobbyesp.docucraft.library.data.work.WorkManagerGateway
import com.bobbyesp.docucraft.library.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.library.domain.usecase.IndexDocumentTextUseCase
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reads documents with WorkManager, which is what lets the reading outlive the screen that asked
 * for it and the process itself: a document saved a moment before the app is closed is still read.
 *
 * One piece of work per document, named after it. A document already waiting or being read keeps
 * the work it has: that work reads whatever is pending when it runs.
 *
 * Where WorkManager is not available (see [WorkManagerGateway]) the document is read in the app, on
 * [fallbackScope], one reading per document at a time. It then lasts only as long as the process;
 * what is left unread is found again from the pages when the app starts.
 */
class WorkManagerDocumentIndexQueue(
    private val workManager: WorkManagerGateway,
    private val indexDocumentText: () -> IndexDocumentTextUseCase,
    private val fallbackScope: CoroutineScope,
) : DocumentIndexQueue {

    private val reading = ConcurrentHashMap.newKeySet<String>()

    override fun enqueue(documentUuid: String) {
        val queued =
            runCatching {
                workManager
                    .get()
                    ?.enqueueUniqueWork(
                        workName(documentUuid),
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<IndexDocumentWorker>()
                            .setInputData(
                                workDataOf(IndexDocumentWorker.KEY_DOCUMENT_UUID to documentUuid)
                            )
                            .build(),
                    )
            }
                .getOrNull() != null
        if (!queued) readInApp(documentUuid)
    }

    private fun readInApp(documentUuid: String) {
        if (!reading.add(documentUuid)) return
        fallbackScope.launch(Dispatchers.Default) {
            try {
                runCatching { indexDocumentText()(documentUuid) }
            } finally {
                reading.remove(documentUuid)
            }
        }
    }

    internal companion object {
        fun workName(documentUuid: String) = "index/$documentUuid"
    }
}
