/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.links

/**
 * Opens what a link leads to outside the app: a web page, a new email, the dialler. Only
 * [LinkAction.OpenWeb], [LinkAction.ComposeEmail] and [LinkAction.Dial] are ever handed to it;
 * [ResolveLinkUseCase] has already refused anything else.
 */
fun interface LinkOpener {
    /**
     * @param look How a web page's browser bar should look, to match the app's theme.
     * @return `false` when no app on the device can open it.
     */
    fun open(action: LinkAction, look: LinkLook): Boolean
}

/** The browser bar's color (ARGB) and whether the app is dark. */
data class LinkLook(val toolbarColor: Int, val darkTheme: Boolean)
