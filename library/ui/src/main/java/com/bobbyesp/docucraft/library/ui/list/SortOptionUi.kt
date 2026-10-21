/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.library.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.library.domain.SortOption
import com.bobbyesp.docucraft.library.ui.R

/**
 * How a [SortOption] is named. Kept out of the model, which only orders documents. Its direction
 * has no icon of its own: Home turns a single arrow, so the change reads as one motion.
 */
@Composable
fun SortOption.Criteria.label(): String =
    when (this) {
        SortOption.Criteria.DATE -> stringResource(R.string.date)
        SortOption.Criteria.NAME -> stringResource(DesignsystemR.string.name)
        SortOption.Criteria.SIZE -> stringResource(R.string.size)
    }
