/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class BasicDocument(
    val uuid: String,
    val filename: String,
    val uri: String,
    val title: String? = null,
    val description: String? = null,
)
