/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Whether the search bar is open and what is typed in it. Pure UI state; what the search *found* is
 * the ViewModel's.
 *
 * @param isOpen Whether the search bar is showing.
 * @param query The initial text, when restored.
 */
@Stable
class ViewerSearchState(isOpen: Boolean, query: String) {

    var isOpen: Boolean by mutableStateOf(isOpen)
        private set

    val field = TextFieldState(initialText = query)

    /**
     * How many times the reader has asked to type. The field takes focus when this goes up, not
     * when the bar is restored after a rotation.
     */
    var focusRequests: Int by mutableIntStateOf(0)
        private set

    /** Opens the bar for typing. */
    fun open() {
        isOpen = true
        focusRequests++
    }

    /** Closes the bar and clears the text. */
    fun close() {
        isOpen = false
        field.clearText()
    }

    companion object {
        val Saver: Saver<ViewerSearchState, Any> =
            listSaver(
                save = { listOf(it.isOpen, it.field.text.toString()) },
                restore = { ViewerSearchState(isOpen = it[0] as Boolean, query = it[1] as String) },
            )
    }
}

/** A [ViewerSearchState] that survives rotation and process death. */
@Composable
fun rememberViewerSearchState(): ViewerSearchState =
    rememberSaveable(saver = ViewerSearchState.Saver) {
        ViewerSearchState(isOpen = false, query = "")
    }
