/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.pages.ViewerPageRequests
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerPageRequestsTest {

    private val requests = ViewerPageRequests()

    /** Retained: a request made before anyone listens is still there when the viewer looks. */
    @Test
    fun aRequestWaitsForTheViewer() = runTest {
        requests.request(DOC_A, 41)

        assertEquals(41, requests.observe(DOC_A).first())
    }

    @Test
    fun aRequestIsTakenOnce() = runTest {
        requests.request(DOC_A, 41)

        assertTrue(requests.consume(DOC_A, 41))
        assertFalse(requests.consume(DOC_A, 41))
        assertNull(requests.observe(DOC_A).first())
    }

    /** Taking an older page must not swallow a newer request made in the meantime. */
    @Test
    fun aNewerRequestSurvivesTakingAnOlderOne() = runTest {
        requests.request(DOC_A, 10)
        requests.request(DOC_A, 20)

        assertFalse(requests.consume(DOC_A, 10))
        assertEquals(20, requests.observe(DOC_A).first())
    }

    @Test
    fun viewersOfOtherDocumentsDoNotSeeIt() = runTest {
        requests.request(DOC_A, 41)

        assertNull(requests.observe(DOC_B).first())
    }

    private companion object {
        val DOC_A = ViewerDocumentRef.Catalogued("a")
        val DOC_B = ViewerDocumentRef.External("content://media/1", "b.pdf")
    }
}
