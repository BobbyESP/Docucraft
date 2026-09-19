/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption

/** How a [SortOption] is named and drawn. Kept out of the model, which only orders documents. */
@Composable
fun SortOption.Criteria.label(): String =
    when (this) {
        SortOption.Criteria.DATE -> stringResource(R.string.date)
        SortOption.Criteria.NAME -> stringResource(R.string.name)
        SortOption.Criteria.SIZE -> stringResource(R.string.size)
    }

fun SortOption.Order.icon(): ImageVector =
    when (this) {
        SortOption.Order.ASC -> Icons.Rounded.ArrowUpward
        SortOption.Order.DESC -> Icons.Rounded.ArrowDownward
    }
