package com.composepdf.renderer

import android.util.Size

/** Immutable result of opening a document session. */
internal data class DocumentResult(
    val documentKey: String,
    val pageSizes: List<Size>,
    val pageCount: Int
)