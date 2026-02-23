package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Stable
class DocumentToolbarState {
    private val _isFindBarOpen = MutableStateFlow(false)
    val isFindBarOpen = _isFindBarOpen.asStateFlow()

    private val _isEditorOpen = MutableStateFlow(false)
    val isEditorOpen = _isEditorOpen.asStateFlow()

    private val _isTextHighlighterOn = MutableStateFlow(false)
    val isTextHighlighterOn = _isTextHighlighterOn.asStateFlow()

    private val _isEditorFreeTextOn = MutableStateFlow(false)
    val isEditorFreeTextOn = _isEditorFreeTextOn.asStateFlow()

    private val _isEditorInkOn = MutableStateFlow(false)
    val isEditorInkOn = _isEditorInkOn.asStateFlow()

    private val _isEditorStampOn = MutableStateFlow(false)
    val isEditorStampOn = _isEditorStampOn.asStateFlow()

    private fun resetStateIfActive(state: MutableStateFlow<Boolean>): Boolean {
        return if (state.value) {
            state.update { false }
            true
        } else {
            false
        }
    }

    fun handleBackPressed(): Boolean {
        val statesInOrder: List<MutableStateFlow<Boolean>> = listOf(
            _isTextHighlighterOn,
            _isEditorFreeTextOn,
            _isEditorInkOn,
            _isEditorStampOn,
            _isEditorOpen,
            _isFindBarOpen
        )

        statesInOrder.forEach { state ->
            if (resetStateIfActive(state)) {
                return true
            }
        }

        return false
    }

    fun updateIsFindBarOpen(value: Boolean) {
        _isFindBarOpen.update { value }
    }

    fun updateIsEditorOpen(value: Boolean) {
        _isEditorOpen.update { value }
    }

    fun updateIsTextHighlighterOn(value: Boolean) {
        _isTextHighlighterOn.update { value }
    }

    fun updateIsEditorFreeTextOn(value: Boolean) {
        _isEditorFreeTextOn.update { value }
    }

    fun updateIsEditorInkOn(value: Boolean) {
        _isEditorInkOn.update { value }
    }

    fun updateIsEditorStampOn(value: Boolean) {
        _isEditorStampOn.update { value }
    }
}