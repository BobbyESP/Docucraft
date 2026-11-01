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
 * Whether the search bar is open, and what is typed in it: presentation alone, like whether the
 * viewer's bars are showing. What the search *found* is the ViewModel's.
 *
 * The text is a [TextFieldState], so it keeps its cursor and selection; the field and the ViewModel
 * meet in the screen, which reports the text as it changes.
 *
 * @param isOpen Whether the search bar is showing.
 * @param query What is typed, when restored.
 */
@Stable
class ViewerSearchState(isOpen: Boolean, query: String) {

    var isOpen: Boolean by mutableStateOf(isOpen)
        private set

    val field = TextFieldState(initialText = query)

    /**
     * How many times the reader has asked to type. The field takes the focus, and so raises the
     * keyboard, when this goes up: not when the bar comes back after a rotation, where the reader
     * did not ask for the keyboard again.
     */
    var focusRequests: Int by mutableIntStateOf(0)
        private set

    /** Opens the bar for typing. */
    fun open() {
        isOpen = true
        focusRequests++
    }

    /** Closes the bar and forgets what was typed: the next search starts empty. */
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

/** Survives rotation and process death, so a search being read is not lost to either. */
@Composable
fun rememberViewerSearchState(): ViewerSearchState =
    rememberSaveable(saver = ViewerSearchState.Saver) {
        ViewerSearchState(isOpen = false, query = "")
    }
