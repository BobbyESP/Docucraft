package com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.usecase

import android.net.Uri
import io.github.vinceglb.filekit.PlatformFile

interface PdfFileManagementUseCase {
    suspend fun copyToSystemStorage(inputUri: Uri, outputFile: PlatformFile)
}