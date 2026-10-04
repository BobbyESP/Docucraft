/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.FakeFoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeTagsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FolderChange
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagChange
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.docscanner.testFolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrganizationUseCasesTest {

    private fun tag(uuid: String, homePosition: Int? = null) =
        Tag(uuid = uuid, name = uuid, color = null, homePosition = homePosition)

    // ---------------- folders ----------------

    @Test
    fun `a new folder is created with its color and icon`() = runTest {
        val folders = FakeFoldersRepository()
        val save = SaveFolderUseCase(folders, newUuid = { "new" })

        val change = save(null, null, " Taxes ", LabelColor.TEAL, FolderIcon.BANK)

        assertEquals(FolderChange.Done, change)
        val folder = folders.getFolder("new")!!
        assertEquals("Taxes", folder.name)
        assertEquals("teal", folder.color)
        assertEquals("account_balance", folder.icon)
    }

    @Test
    fun `the default icon is kept as no icon`() = runTest {
        val folders = FakeFoldersRepository()
        SaveFolderUseCase(folders, newUuid = { "new" })(null, null, "A", null, FolderIcon.Default)

        assertNull(folders.getFolder("new")!!.icon)
    }

    @Test
    fun `a name that is taken leaves the folder as it was`() = runTest {
        val folders =
            FakeFoldersRepository(folders = listOf(testFolder("a", "Taxes"), testFolder("b", "B")))
        val save = SaveFolderUseCase(folders)

        val change = save("b", null, "taxes", LabelColor.RED, FolderIcon.STAR)

        assertEquals(FolderChange.NameTaken, change)
        assertEquals("B", folders.getFolder("b")!!.name)
        assertNull(folders.getFolder("b")!!.color)
    }

    @Test
    fun `no folder is created deeper than the app lets folders go`() = runTest {
        // A chain as deep as allowed: each folder inside the one before it.
        val chain =
            (1..FolderDepth.MAX).map { depth ->
                testFolder("f$depth", parentUuid = if (depth == 1) null else "f${depth - 1}")
            }
        val folders = FakeFoldersRepository(folders = chain)
        val save = SaveFolderUseCase(folders, newUuid = { "new" })

        val change = save(null, "f${FolderDepth.MAX}", "Too deep", null, FolderIcon.Default)

        assertEquals(FolderChange.NotFound, change)
        assertNull(folders.getFolder("new"))
    }

    // ---------------- tags ----------------

    @Test
    fun `creating a tag with a name another has is refused`() = runTest {
        val tags = FakeTagsRepository(tags = listOf(tag("Invoices")))

        val change = SaveTagUseCase(tags, newUuid = { "new" })(null, "invoices ", LabelColor.AMBER)

        assertEquals(TagChange.NameTaken, change)
        assertEquals(1, tags.tags.value.size)
        assertNull(tags.tags.value.single().color)
    }

    @Test
    fun `a new tag is created with its color`() = runTest {
        val tags = FakeTagsRepository()

        val change = SaveTagUseCase(tags, newUuid = { "new" })(null, "Health", LabelColor.GREEN)

        assertEquals(TagChange.Done, change)
        assertEquals("green", tags.tags.value.single().color)
    }

    @Test
    fun `typing the name of a tag that exists puts that tag on the document`() = runTest {
        val tags = FakeTagsRepository(tags = listOf(tag("Invoices")))

        TagDocumentByNameUseCase(tags, newUuid = { "new" })("doc", "INVOICES")

        assertEquals(1, tags.tags.value.size)
        assertEquals(setOf("Invoices"), tags.assignments.value["doc"])
    }

    // ---------------- Home ----------------

    @Test
    fun `a tag given a section goes after the ones that have one`() = runTest {
        val all = listOf(tag("a", homePosition = 0), tag("b"), tag("c", homePosition = 1))
        val tags = FakeTagsRepository(tags = all)

        ArrangeHomeSectionsUseCase(tags).setShown("b", shown = true)

        assertEquals(listOf("a", "c", "b"), sectionsOf(tags))
    }

    @Test
    fun `a section moves one place and stops at the ends`() = runTest {
        val all = listOf(tag("a", homePosition = 0), tag("b", homePosition = 1))
        val tags = FakeTagsRepository(tags = all)
        val arrange = ArrangeHomeSectionsUseCase(tags)

        arrange.move("b", by = -1)
        assertEquals(listOf("b", "a"), sectionsOf(tags))

        arrange.move("b", by = -1)
        assertEquals(listOf("b", "a"), sectionsOf(tags))
    }

    @Test
    fun `Home shows the pinned folders and a section for each chosen tag in order`() = runTest {
        val document = testDocument("doc")
        val folders =
            FakeFoldersRepository(
                folders =
                    listOf(
                        testFolder("late", pinnedAtEpochMillis = 2),
                        testFolder("unpinned"),
                        testFolder("early", pinnedAtEpochMillis = 1),
                    )
            )
        val tags =
            FakeTagsRepository(
                tags = listOf(tag("second", homePosition = 1), tag("none"), tag("first", 0)),
                documents = listOf(document),
                assignments = mapOf("doc" to setOf("first", "none")),
            )

        val sections = ObserveHomeSectionsUseCase(folders, tags)().first()

        assertEquals(listOf("early", "late"), sections.pinnedFolders.map { it.uuid })
        assertEquals(listOf("first", "second"), sections.tagSections.map { it.tag.uuid })
        assertEquals(listOf(document), sections.tagSections[0].documents)
        assertEquals(emptyList<Any>(), sections.tagSections[1].documents)
    }

    private fun sectionsOf(tags: FakeTagsRepository): List<String> =
        tags.tags.value
            .filter { it.homePosition != null }
            .sortedBy { it.homePosition }
            .map { it.uuid }
}
