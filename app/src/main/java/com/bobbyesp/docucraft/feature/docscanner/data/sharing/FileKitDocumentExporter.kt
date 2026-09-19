/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.sharing

import android.os.Environment
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentExporter
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.ExportOutcome
import com.bobbyesp.scanner.ContentRef
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path

/** Asks the user where to put the document through the system file saver, then copies it there. */
class FileKitDocumentExporter : DocumentExporter {

    override suspend fun export(document: ContentRef, suggestedName: String): ExportOutcome {
        val destination =
            try {
                FileKit.openFileSaver(
                    suggestedName = suggestedName,
                    defaultExtension = EXTENSION,
                    directory = defaultDirectory(),
                    dialogSettings = FileKitDialogSettings.createDefault(),
                )
            } catch (e: Exception) {
                return ExportOutcome.Failed(e)
            } ?: return ExportOutcome.Cancelled

        return try {
            PlatformFile(document.value.toUri()).copyTo(destination)

            if (destination.exists()) {
                ExportOutcome.Saved(ContentRef(destination.path))
            } else {
                ExportOutcome.Failed(IllegalStateException("The exported file is not there"))
            }
        } catch (e: Exception) {
            ExportOutcome.Failed(e)
        }
    }

    private fun defaultDirectory(): PlatformFile =
        PlatformFile(
            PlatformFile(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            ),
            APP_DIRECTORY,
        )

    private companion object {
        const val EXTENSION = "pdf"
        const val APP_DIRECTORY = "Docucraft"
    }
}
