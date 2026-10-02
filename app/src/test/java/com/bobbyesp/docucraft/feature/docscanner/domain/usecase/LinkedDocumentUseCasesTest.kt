/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeExternalDocumentAccess
import com.bobbyesp.docucraft.feature.docscanner.FakeLinkedDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkedDocumentFacts
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.NewLinkedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.MeasuredFile
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.docscanner.testLinkedDocument
import com.bobbyesp.scanner.ContentRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A PDF of another app is referred to, never copied or changed. These are the rules of that
 * reference: when it is made, what is held for it, and what is given back when it goes.
 */
class LinkedDocumentUseCasesTest {

    private val linked = FakeLinkedDocumentsRepository()
    private val access = FakeExternalDocumentAccess()
    private val register = RegisterLinkedDocumentUseCase(access, linked)
    private val forget = ForgetLinkedDocumentUseCase(linked, access)

    // --- registering ---

    @Test
    fun `a document of another app is registered by where it is and what it is called`() = runTest {
        val uuid = register(SHARED, "Contract 2026.pdf")

        assertEquals("linked-1", uuid)
        assertEquals(
            listOf(NewLinkedDocument(SHARED, "Contract 2026", hasPersistedPermission = false)),
            linked.registered.map { it.first },
        )
    }

    /** As every document's name: the extension is the file's, not the document's. */
    @Test
    fun `its name loses the extension whatever its case, and nothing else`() = runTest {
        register(SHARED, "SCAN.PDF")
        register(SHARED, "notes.final")
        register(SHARED, "pdf")

        assertEquals(
            listOf("SCAN", "notes.final", "pdf"),
            linked.registered.map { it.first.originalName },
        )
    }

    @Test
    fun `the permission is asked for before anything else, and what came of it is noted`() =
        runTest {
            access.keepable += SHARED

            register(SHARED, "a.pdf")

            assertEquals("keep" to SHARED, access.calls.first())
            assertTrue(linked.registered.single().first.hasPersistedPermission)
        }

    @Test
    fun `no more than fifty are kept`() = runTest {
        register(SHARED, "a.pdf")

        assertEquals(50, linked.registered.single().second)
    }

    /** The platform keeps a limited number of permissions for an app. */
    @Test
    fun `what was held for the documents forgotten to make room is given back`() = runTest {
        linked.forgottenOnRegister = listOf(OLD, OLDER)

        register(SHARED, "a.pdf")

        assertEquals(listOf("release" to OLD, "release" to OLDER), access.calls.drop(1))
    }

    @Test
    fun `opening the same location again is the same document`() = runTest {
        assertEquals(register(SHARED, "a.pdf"), register(SHARED, "a.pdf"))
    }

    // --- forgetting ---

    @Test
    fun `forgetting a document gives back what was held for it`() = runTest {
        val uuid = register(SHARED, "a.pdf")

        forget(uuid)

        assertEquals(listOf(uuid), linked.forgotten)
        assertEquals("release" to SHARED, access.calls.last())
    }

    @Test
    fun `forgetting what is not a linked document gives nothing back`() = runTest {
        forget("doc-1")

        assertTrue(access.calls.isEmpty())
    }

    // --- describing ---

    @Test
    fun `a linked document is read once it has opened, and what was found is noted`() = runTest {
        val document = testLinkedDocument(uuid = "linked-1", location = SHARED)
        access.files[SHARED] = MeasuredFile(sizeBytes = 2_048, contentHash = "abc")
        val describe = describeFor(FakeDocumentsRepository(linked = listOf(document)))

        describe("linked-1", pageCount = 12, isProtected = false)

        assertEquals(
            listOf("linked-1" to LinkedDocumentFacts(2_048, "abc", 12, isProtected = false)),
            linked.described,
        )
    }

    /** It asked for a password: it was reached, and nothing more can be said of it. */
    @Test
    fun `a protected document is noted as such even when it cannot be read`() = runTest {
        val document = testLinkedDocument(uuid = "linked-1", location = SHARED)
        val describe = describeFor(FakeDocumentsRepository(linked = listOf(document)))

        describe("linked-1", pageCount = null, isProtected = true)

        assertEquals(
            listOf("linked-1" to LinkedDocumentFacts(null, null, null, isProtected = true)),
            linked.described,
        )
    }

    /** The app knows its own documents already, and reading one whole costs what it weighs. */
    @Test
    fun `a document the app keeps is neither read nor described`() = runTest {
        val describe = describeFor(FakeDocumentsRepository(listOf(testDocument(uuid = "doc-1"))))

        describe("doc-1", pageCount = 3, isProtected = false)
        describe("no-such-document", pageCount = 3, isProtected = false)

        assertTrue(access.calls.isEmpty())
        assertTrue(linked.described.isEmpty())
    }

    private fun describeFor(documents: FakeDocumentsRepository) =
        DescribeLinkedDocumentUseCase(documents, linked, access)

    private companion object {
        val SHARED = ContentRef("content://other.app/documents/7")
        val OLD = ContentRef("content://other.app/documents/2")
        val OLDER = ContentRef("content://other.app/documents/1")
    }
}
