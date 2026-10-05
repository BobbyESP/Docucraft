/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.store

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentActivityRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentThumbnails
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.IndexDocumentTextUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveFolderUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveTagUseCase
import com.bobbyesp.docucraft.test.R
import com.bobbyesp.scanner.ContentRef
import java.io.File
import java.util.UUID
import org.koin.core.Koin

/**
 * The library the store's screenshots show, in one language: a tenant's paperwork. A rental
 * contract, the bills of a flat, the receipts of three purchases, a letter from the bank and a
 * student's notes, in four folders and under two tags. All of it invented.
 *
 * The same documents in every language, as the screenshots' design asks, each written in that
 * language (`store_samples.xml`). They enter the catalogue the way a scan does: stored by the app's
 * storage, catalogued by its repository, and read by the reader that makes them searchable.
 *
 * @property contract The rental contract, shown in the viewer and in the details.
 * @property notes The notes, shown in the viewer at night.
 * @property bankLetter The letter with a link, shown with the link's preview.
 * @property receipt The bill the scanner's stand-in frames.
 * @property billsFolder The folder shown open.
 * @property searchQuery A word four of the documents say on their pages, and none in its name.
 */
internal class SampleLibrary(
    val contract: String,
    val notes: String,
    val bankLetter: String,
    val receipt: String,
    val billsFolder: String,
    val searchQuery: String,
    private val documentUuids: List<String>,
) {

    /**
     * Takes the sample documents' files and previews out of the app's storage. Their catalogue goes
     * with its own file; these are in the directories the app's own documents are in, where they
     * must not be left.
     */
    suspend fun remove(koin: Koin) {
        val storage: DocumentStorage = koin.get()
        val thumbnails: DocumentThumbnails = koin.get()
        for (uuid in documentUuids) {
            runCatching { storage.delete("documents/$uuid.pdf") }
            runCatching { thumbnails.discard(uuid) }
        }
    }

    companion object {
        /** Where the link in the bank's letter leads: a name reserved for examples. */
        const val BANK_HOST = "bank.example.com"
        private const val BANK_URL = "https://$BANK_HOST/statements"

        /**
         * Writes the sample library in [language] into the catalogue of [koin].
         *
         * @param strings The sample's own strings, already in [language].
         * @param context The app's, for somewhere to write each document before it is stored.
         */
        suspend fun create(
            koin: Koin,
            context: Context,
            strings: Resources,
            language: String,
        ): SampleLibrary {
            val writer = Writer(koin, context, strings, language)
            val text = { id: Int -> strings.getString(id) }

            // Saved oldest first: Home lists the newest at the top, and the three it opens with
            // are the three the screenshots go on to show.
            val council =
                writer.bill(
                    "council",
                    "Aldea",
                    R.string.sample_council_title,
                    R.string.sample_council_description,
                    R.string.sample_council_body,
                )
            val water =
                writer.bill(
                    "water",
                    "Aquavalle",
                    R.string.sample_water_title,
                    R.string.sample_water_description,
                    R.string.sample_water_body,
                )
            val internet =
                writer.bill(
                    "internet",
                    "Fibranet",
                    R.string.sample_internet_title,
                    R.string.sample_internet_description,
                    R.string.sample_internet_body,
                )
            val gas =
                writer.bill(
                    "gas",
                    "Calora",
                    R.string.sample_gas_title,
                    R.string.sample_gas_description,
                    R.string.sample_gas_body,
                )
            val phone =
                writer.bill(
                    "phone",
                    "Movilia",
                    R.string.sample_phone_title,
                    R.string.sample_phone_description,
                    R.string.sample_phone_body,
                )
            val laptop =
                writer.bill(
                    "laptop",
                    "Tecnoria",
                    R.string.sample_laptop_title,
                    R.string.sample_laptop_description,
                    R.string.sample_laptop_body,
                )
            val washing =
                writer.bill(
                    "washing",
                    "Hogara",
                    R.string.sample_washing_title,
                    R.string.sample_washing_description,
                    R.string.sample_washing_body,
                )
            val bank =
                writer.letter(
                    slug = "bank",
                    sender = "Meridia",
                    title = R.string.sample_bank_title,
                    description = R.string.sample_bank_description,
                    paragraphs = listOf(R.string.sample_bank_body_1, R.string.sample_bank_body_2),
                    link = BANK_HOST to BANK_URL,
                    closing = R.string.sample_bank_body_3,
                )
            val notes =
                writer.chapters(
                    slug = "notes",
                    title = R.string.sample_notes_title,
                    description = R.string.sample_notes_description,
                    heading = R.string.sample_section,
                    paragraphs =
                        listOf(
                            R.string.sample_notes_body_1,
                            R.string.sample_notes_body_2,
                            R.string.sample_notes_body_3,
                        ),
                    pages = 12,
                )
            val electricity =
                writer.bill(
                    "electricity",
                    "Lumena",
                    R.string.sample_electricity_title,
                    R.string.sample_electricity_description,
                    R.string.sample_electricity_body,
                )
            val contract =
                writer.chapters(
                    slug = "contract",
                    title = R.string.sample_contract_title,
                    description = R.string.sample_contract_description,
                    heading = R.string.sample_clause,
                    paragraphs =
                        listOf(
                            R.string.sample_contract_body_1,
                            R.string.sample_contract_body_2,
                            R.string.sample_contract_body_3,
                        ),
                    pages = 4,
                )

            val bills =
                writer.folder(
                    "bills",
                    text(R.string.sample_folder_bills),
                    LabelColor.AMBER,
                    FolderIcon.RECEIPT,
                    pinned = true,
                )
            val university =
                writer.folder(
                    "university",
                    text(R.string.sample_folder_university),
                    LabelColor.GREEN,
                    FolderIcon.SCHOOL,
                    pinned = true,
                )
            val home =
                writer.folder(
                    "home",
                    text(R.string.sample_folder_home),
                    LabelColor.TERRACOTTA,
                    FolderIcon.HOME,
                )
            val taxes =
                writer.folder(
                    "taxes",
                    text(R.string.sample_folder_taxes),
                    LabelColor.TEAL,
                    FolderIcon.BANK,
                    parent = bills,
                )
            val folders: FoldersRepository = koin.get()
            folders.moveDocuments(listOf(water, internet, gas, electricity), bills)
            folders.moveDocuments(listOf(council), taxes)
            folders.moveDocuments(listOf(notes), university)
            folders.moveDocuments(listOf(contract, washing), home)

            writer.tag(
                "contract",
                text(R.string.sample_tag_contract),
                LabelColor.INDIGO,
                listOf(contract),
            )
            writer.tag(
                "warranty",
                text(R.string.sample_tag_warranty),
                LabelColor.TEAL,
                listOf(washing, laptop, phone),
            )
            writer.tag(
                "2026",
                "2026",
                LabelColor.AMBER,
                listOf(contract, electricity, gas, council),
            )

            val documents: DocumentsRepository = koin.get()
            documents.setFavorite(contract, true)
            documents.setFavorite(electricity, true)

            // Home opens with what was read last.
            val activity: DocumentActivityRepository = koin.get()
            for (uuid in listOf(notes, electricity, contract)) activity.recordOpened(uuid)

            return SampleLibrary(
                contract = contract,
                notes = notes,
                bankLetter = bank,
                receipt = electricity,
                billsFolder = bills,
                searchQuery = text(R.string.sample_search_query),
                documentUuids = writer.documents,
            )
        }
    }

    private class Writer(
        private val koin: Koin,
        private val context: Context,
        private val strings: Resources,
        private val language: String,
    ) {
        private val storage: DocumentStorage = koin.get()
        private val repository: DocumentsRepository = koin.get()
        private val folders: FoldersRepository = koin.get()
        private val tags: TagsRepository = koin.get()
        private val indexText: IndexDocumentTextUseCase = koin.get()
        private val clock = CaptureClock()

        val documents = mutableListOf<String>()

        /**
         * The same uuid each time for the same thing in the same language: what the app keeps by
         * uuid, such as a document's preview, is then written over rather than piled up.
         */
        private fun uuidOf(kind: String, slug: String): String =
            UUID.nameUUIDFromBytes("store-capture/$language/$kind/$slug".toByteArray()).toString()

        /** One page from a company: a bill, a receipt or an invoice. */
        suspend fun bill(
            slug: String,
            sender: String,
            @StringRes title: Int,
            @StringRes description: Int,
            @StringRes body: Int,
        ): String = letter(slug, sender, title, description, paragraphs = listOf(body))

        /** One page with a letterhead, a title and its paragraphs, and a link if it has one. */
        suspend fun letter(
            slug: String,
            sender: String,
            @StringRes title: Int,
            @StringRes description: Int,
            paragraphs: List<Int>,
            link: Pair<String, String>? = null,
            @StringRes closing: Int? = null,
        ): String {
            val pdf = SamplePdf()
            pdf.page {
                box(0f, 0f, SamplePdf.WIDTH, 96f, SamplePdf.TINT)
                text(MARGIN, 60f, sender, size = 24f, bold = true)
                text(MARGIN, 168f, strings.getString(title), size = 28f, bold = true)
                text(
                    MARGIN,
                    194f,
                    strings.getString(description),
                    size = 14f,
                    gray = SamplePdf.MUTED,
                )
                rule(MARGIN, 214f, COLUMN)
                var y = 256f
                for (paragraph in paragraphs) {
                    y =
                        paragraph(MARGIN, y, COLUMN, strings.getString(paragraph), BODY, LEADING) +
                            12f
                }
                if (link != null) {
                    link(MARGIN, y + 6f, link.first, link.second, size = 17f)
                    y += 52f
                }
                if (closing != null) {
                    y =
                        paragraph(
                            MARGIN,
                            y,
                            COLUMN,
                            strings.getString(closing),
                            BODY,
                            LEADING,
                            SamplePdf.MUTED,
                        )
                }
                rule(MARGIN, y + 16f, COLUMN)
                // What a table of amounts looks like from a distance: these pages are mostly seen
                // as previews a few millimetres wide.
                repeat(5) { row ->
                    val top = y + 44f + row * 34f
                    box(MARGIN, top, COLUMN * 0.52f, 9f, SamplePdf.RULE)
                    box(MARGIN + COLUMN * 0.8f, top, COLUMN * 0.2f, 9f, SamplePdf.RULE)
                }
            }
            return save(slug, pdf, title, description)
        }

        /** Several pages of numbered parts: a contract's clauses, or a subject's sections. */
        suspend fun chapters(
            slug: String,
            @StringRes title: Int,
            @StringRes description: Int,
            @StringRes heading: Int,
            paragraphs: List<Int>,
            pages: Int,
        ): String {
            val pdf = SamplePdf()
            var part = 1
            repeat(pages) { index ->
                pdf.page {
                    var y = 84f
                    if (index == 0) {
                        text(MARGIN, 96f, strings.getString(title), size = 30f, bold = true)
                        text(
                            MARGIN,
                            124f,
                            strings.getString(description),
                            size = 14f,
                            gray = SamplePdf.MUTED,
                        )
                        rule(MARGIN, 146f, COLUMN)
                        y = 190f
                    }
                    while (y < SamplePdf.HEIGHT - 150f) {
                        text(MARGIN, y, strings.getString(heading, part), size = 17f, bold = true)
                        val body = strings.getString(paragraphs[(part - 1) % paragraphs.size])
                        y = paragraph(MARGIN, y + 28f, COLUMN, body, BODY, LEADING) + 22f
                        part++
                    }
                    text(
                        SamplePdf.WIDTH / 2 - 4f,
                        SamplePdf.HEIGHT - 44f,
                        "${index + 1}",
                        size = 11f,
                        gray = SamplePdf.MUTED,
                    )
                }
            }
            return save(slug, pdf, title, description)
        }

        private suspend fun save(
            slug: String,
            pdf: SamplePdf,
            @StringRes title: Int,
            @StringRes description: Int,
        ): String {
            val uuid = uuidOf("document", slug)
            val scanned = File(context.cacheDir, "store-capture-$slug.pdf")
            scanned.writeBytes(pdf.bytes())
            try {
                val stored = storage.storeDocument(ContentRef(scanned.toUri().toString()), uuid)
                documents += uuid
                repository.addScan(
                    NewScan(
                        uuid = uuid,
                        originalName = "Scan_$slug",
                        filePath = stored.filePath,
                        sizeBytes = stored.sizeBytes,
                        contentHash = stored.contentHash,
                        pageCount = pdf.pageCount,
                        capturedAtEpochMillis = clock.next(),
                    )
                )
            } finally {
                scanned.delete()
            }
            repository.modifyFields(uuid, strings.getString(title), strings.getString(description))
            indexText(uuid)
            return uuid
        }

        suspend fun folder(
            slug: String,
            name: String,
            color: LabelColor,
            icon: FolderIcon,
            parent: String? = null,
            pinned: Boolean = false,
        ): String {
            val uuid = uuidOf("folder", slug)
            SaveFolderUseCase(folders, newUuid = { uuid })(
                folderUuid = null,
                parentUuid = parent,
                name = name,
                color = color,
                icon = icon,
            )
            if (pinned) folders.setPinned(uuid, true)
            return uuid
        }

        suspend fun tag(slug: String, name: String, color: LabelColor, documents: List<String>) {
            val uuid = uuidOf("tag", slug)
            SaveTagUseCase(tags, newUuid = { uuid })(tagUuid = null, name = name, color = color)
            for (document in documents) tags.tag(document, uuid)
        }

        private companion object {
            const val MARGIN = 56f
            const val COLUMN = SamplePdf.WIDTH - 2 * MARGIN
            const val BODY = 15f
            const val LEADING = 23f
        }
    }
}
