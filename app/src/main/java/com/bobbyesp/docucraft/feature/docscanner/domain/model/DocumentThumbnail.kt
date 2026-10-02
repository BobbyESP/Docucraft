/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

/**
 * Which preview to show for a document: the one of this version of its content.
 *
 * A preview is not a fact about a document that the catalogue keeps. It is a picture of its first
 * page that can be drawn again at any time, so it is named by what it is drawn from and looked for
 * in a cache. A document whose content changes asks for another one.
 *
 * @property contentVersion When the document's content last changed.
 */
data class DocumentThumbnail(val documentUuid: String, val contentVersion: Long)
