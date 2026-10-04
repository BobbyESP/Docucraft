/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.details

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentOrigin
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentThumbnail
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.DocumentText
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.scanner.ContentRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What the details of an open document show. Any of it may be unknown for a document from another
 * app, which the catalogue has never seen.
 *
 * @property fileName What its file is called, when that is not what the document is called.
 * @property pdfVersion The version of PDF its file says it is, such as "1.7".
 * @property text Whether it has text to select; `null` while that is still being looked into.
 * @property library What the library knows of it; `null` for a document the app does not keep.
 */
data class ViewerDocumentDetails(
    val name: String,
    val description: String?,
    val pageCount: Int?,
    val sizeBytes: Long?,
    val text: DocumentText? = null,
    val fileName: String? = null,
    val pdfVersion: String? = null,
    val library: LibraryDetails? = null,
)

/**
 * What only a document the app keeps has: how it got here, when, and where the user put it.
 *
 * @property enteredAtEpochMillis When it was scanned, or brought in from another app.
 * @property modifiedAtEpochMillis When its content last changed, if it has since it entered.
 * @property folder The folder it is in, or `null` in the root.
 */
data class LibraryDetails(
    val origin: DocumentOrigin,
    val enteredAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long?,
    val folder: Folder?,
    val tags: List<Tag>,
    val isFavorite: Boolean,
    val thumbnail: DocumentThumbnail,
)

/** Reads what can be learnt about a document from the file itself. */
interface DocumentFactsReader {
    suspend fun read(document: ContentRef): DocumentFacts

    /**
     * Only the version of PDF the file says it is, for a document whose size and pages are already
     * known: it takes the first bytes of the file, not a renderer.
     */
    suspend fun pdfVersion(document: ContentRef): String?
}

data class DocumentFacts(val sizeBytes: Long?, val pageCount: Int?, val pdfVersion: String? = null)

/**
 * The version in the header every PDF starts with (`%PDF-1.7`), or `null` when [header] has none.
 * Looked for rather than expected at the first byte: some files carry a few bytes before it, which
 * readers accept.
 */
fun pdfVersionIn(header: String): String? = PdfHeader.find(header)?.groupValues?.get(1)

private val PdfHeader = Regex("""%PDF-(\d\.\d)""")

/**
 * The details of the document a viewer shows, emitting `null` once it is gone.
 *
 * A catalogued document's come from the catalogue, which already knows its size and page count; the
 * viewer used to recompute the size through the content resolver from a composable, and to take the
 * page count from the rendering engine. An external document's are read from the file, once.
 *
 * What takes reading the file follows: the version of PDF comes with the first details, since it is
 * in the file's first bytes, and whether the document has text takes reading some of its pages, so
 * the details come out without it and again with it. The file is read once for as long as the
 * document stays where it is, not again each time it is renamed or tagged.
 *
 * @param folderOf The folder a document is in, as `FoldersRepository.observeFolderOf` says it.
 * @param tagsOf The tags a document carries, as `TagsRepository.observeTagsOf` says them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveViewerDocumentDetailsUseCase(
    private val observeDocument: ObserveDocumentUseCase,
    private val folderOf: (String) -> Flow<Folder?>,
    private val tagsOf: (String) -> Flow<List<Tag>>,
    private val facts: DocumentFactsReader,
    private val detectText: suspend (DocumentSource) -> DocumentText,
) {
    operator fun invoke(ref: ViewerDocumentRef): Flow<ViewerDocumentDetails?> =
        when (ref) {
            is ViewerDocumentRef.Catalogued ->
                combine(
                    observeDocument(ref.uuid),
                    folderOf(ref.uuid),
                    tagsOf(ref.uuid),
                    fileOf(ref.uuid),
                ) { document, folder, tags, file ->
                    document?.let { details(it, folder, tags, file) }
                }

            is ViewerDocumentRef.External ->
                flow {
                    val read = facts.read(ContentRef(ref.uri))
                    val details =
                        ViewerDocumentDetails(
                            name = ref.displayName,
                            description = null,
                            pageCount = read.pageCount,
                            sizeBytes = read.sizeBytes,
                            pdfVersion = read.pdfVersion,
                        )
                    emit(details)
                    emit(details.copy(text = detectText(DocumentSource(ref.uri))))
                }
        }

    private fun details(document: Document, folder: Folder?, tags: List<Tag>, file: FileKnowledge) =
        ViewerDocumentDetails(
            name = document.name,
            description = document.description,
            pageCount = document.pageCount,
            sizeBytes = document.sizeBytes,
            text = file.text,
            fileName =
                "${document.originalName}.pdf".takeIf { document.name != document.originalName },
            pdfVersion = file.pdfVersion,
            library =
                (document as? Document.Managed)?.let { managed ->
                    LibraryDetails(
                        origin = managed.origin,
                        enteredAtEpochMillis =
                            managed.capturedAtEpochMillis ?: managed.createdAtEpochMillis,
                        modifiedAtEpochMillis =
                            managed.contentUpdatedAtEpochMillis.takeIf {
                                it > managed.createdAtEpochMillis
                            },
                        folder = folder,
                        tags = tags,
                        isFavorite = managed.isFavorite,
                        thumbnail = managed.thumbnail,
                    )
                },
        )

    /** What reading the file of the document [uuid] says, read again only if the file moves. */
    private fun fileOf(uuid: String): Flow<FileKnowledge> =
        observeDocument(uuid)
            .map { it?.location }
            .distinctUntilChanged()
            .flatMapLatest { location ->
                if (location == null) {
                    flowOf(FileKnowledge())
                } else {
                    flow {
                        val known = FileKnowledge(pdfVersion = facts.pdfVersion(location))
                        emit(known)
                        emit(known.copy(text = detectText(DocumentSource(location.value))))
                    }
                }
            }

    private data class FileKnowledge(val pdfVersion: String? = null, val text: DocumentText? = null)
}
