package com.bobbyesp.docucraft.feature.shared.domain

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Stable
@Serializable
data class BasicDocument(
    val uuid: String,
    val filename: String,
    val uri: String,
    val title: String? = null,
    val description: String? = null,
)