/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.util.viewModel

import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.util.events.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The two pipes out of a state holder carry things with opposite requirements, and the difference
 * is the point.
 *
 * An **effect** is for whoever is on screen now. Nobody listening means nobody to act, and the
 * moment has passed. A **message** is for the user, who may not be looking yet.
 *
 * They used to share one buffered channel, so a navigation effect raised while its screen was off
 * screen queued up and was carried out later, against whatever the user was doing by then. That is
 * how tapping a document came to open settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an effect raised while somebody is listening is delivered`() =
        runTest(dispatcher) {
            val viewModel = ProbeViewModel()
            val delivered = async { viewModel.effects.first() }
            runCurrent()

            viewModel.raise(ProbeEffect.GoSomewhere)

            assertEquals(ProbeEffect.GoSomewhere, delivered.await())
        }

    /** The bug, pinned: a command nobody took is a command that no longer applies. */
    @Test
    fun `an effect raised with nobody listening is dropped, not kept`() =
        runTest(dispatcher) {
            val viewModel = ProbeViewModel()

            viewModel.raise(ProbeEffect.GoSomewhere)
            advanceUntilIdle()

            assertNull(
                "A later collector must not inherit a command raised before it existed",
                withTimeoutOrNull(1_000) { viewModel.effects.first() },
            )
        }

    @Test
    fun `effects raised with nobody listening do not queue up`() =
        runTest(dispatcher) {
            val viewModel = ProbeViewModel()

            viewModel.raise(ProbeEffect.GoSomewhere)
            viewModel.raise(ProbeEffect.GoSomewhereElse)
            advanceUntilIdle()

            assertNull(withTimeoutOrNull(1_000) { viewModel.effects.first() })
        }

    /**
     * The deliberate asymmetry. A scan that finished while the catalogue was off screen still has
     * to report what happened, so this one waits.
     */
    @Test
    fun `a message raised with nobody listening waits for a reader`() =
        runTest(dispatcher) {
            val viewModel = ProbeViewModel()

            viewModel.tell("the scan was saved")
            advanceUntilIdle()

            assertNotNull(
                "A message must survive until somebody can read it",
                withTimeoutOrNull(1_000) { viewModel.defaultEvents.first() },
            )
        }

    // ---------------- probe ----------------

    private sealed interface ProbeEffect {
        data object GoSomewhere : ProbeEffect

        data object GoSomewhereElse : ProbeEffect
    }

    private class ProbeViewModel : BaseViewModel<Unit, Unit, ProbeEffect>(initialState = Unit) {

        override fun onHandleIntent(intent: Unit) = Unit

        fun raise(effect: ProbeEffect) = sendEffect(effect)

        fun tell(message: String) =
            sendUiEvent(UiEvent.ShowMessage(message, NotificationType.Success))
    }
}
