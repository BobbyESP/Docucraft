package com.bobbyesp.docucraft.feature.docscanner.domain.model

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Stable
@Serializable
data class BasicDocumentInfo(
    val id: String,
    val filename: String,
    val uri: String,
    val title: String? = null,
    val description: String? = null,
)
