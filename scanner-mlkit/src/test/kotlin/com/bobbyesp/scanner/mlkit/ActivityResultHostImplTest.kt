/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.scanner.mlkit

import androidx.activity.result.ActivityResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of process-death recovery that lives here: a result whose caller no longer exists has to
 * be kept, and a caller that comes back on a stale belief has to be told there is nothing to wait
 * for rather than waiting forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityResultHostImplTest {

    private val host = ActivityResultHostImpl()

    private fun result(code: Int = -1) = ActivityResult(code, null)

    @Test
    fun `a result nobody is waiting for is kept, not dropped`() = runTest {
        host.deliver(result(code = 42))

        assertEquals(42, host.awaitPendingResult()?.resultCode)
    }

    @Test
    fun `a kept result is handed over only once`() = runTest {
        host.deliver(result())

        assertNull(host.awaitPendingResult().let { host.awaitPendingResult() })
    }

    @Test
    fun `nothing outstanding means nothing to wait for`() = runTest {
        assertNull(host.awaitPendingResult())
    }

    /**
     * The registry re-delivers after the process is rebuilt; the claim may come first or second.
     */
    @Test
    fun `a restored launch waits for the result still to arrive`() = runTest {
        host.restorePendingLaunch(pending = true)

        val claim = async { host.awaitPendingResult() }
        advanceUntilIdle()

        host.deliver(result(code = 7))
        advanceUntilIdle()

        assertEquals(7, claim.await()?.resultCode)
    }

    @Test
    fun `a restored launch whose result already arrived does not wait`() = runTest {
        host.deliver(result(code = 7))
        // The registry re-delivers during onCreate, which can beat the Activity restoring its flag.
        host.restorePendingLaunch(pending = true)

        assertEquals(7, host.awaitPendingResult()?.resultCode)
    }

    @Test
    fun `hasPendingLaunch reports what the Activity should persist`() = runTest {
        assertFalse(host.hasPendingLaunch)

        host.restorePendingLaunch(pending = true)
        assertTrue(host.hasPendingLaunch)

        host.deliver(result())
        assertTrue("a kept result is still owed to somebody", host.hasPendingLaunch)

        host.awaitPendingResult()
        assertFalse(host.hasPendingLaunch)
    }
}
