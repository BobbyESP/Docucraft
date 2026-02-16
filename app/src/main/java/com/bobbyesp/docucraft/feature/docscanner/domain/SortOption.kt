package com.bobbyesp.docucraft.feature.docscanner.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.bobbyesp.docucraft.R

@Immutable
data class SortOption(val criteria: Criteria, val order: Order) {
    enum class Criteria {
        DATE,
        NAME,
        SIZE;

        @Composable
        fun getLocalizedName(): String {
            return when (this) {
                DATE -> stringResource(R.string.date)
                NAME -> stringResource(R.string.name)
                SIZE -> stringResource(R.string.size)
            }
        }

        companion object {
            @Composable fun toString(criteria: Criteria): String = criteria.getLocalizedName()
        }
    }

    enum class Order {
        ASC,
        DESC;

        fun reverse(): Order {
            return when (this) {
                ASC -> DESC
                DESC -> ASC
            }
        }
    }

    @Composable
    fun getSortIcon(): ImageVector {
        return when (order) {
            Order.ASC -> Icons.Rounded.ArrowUpward
            Order.DESC -> Icons.Rounded.ArrowDownward
        }
    }

    @Composable
    fun getName(): String {
        return when (criteria) {
            Criteria.DATE -> stringResource(R.string.date)
            Criteria.NAME -> stringResource(R.string.name)
            Criteria.SIZE -> stringResource(R.string.size)
        }
    }

    companion object {
        val DateAsc = SortOption(Criteria.DATE, Order.ASC)
        val DateDesc = SortOption(Criteria.DATE, Order.DESC)
        val NameAsc = SortOption(Criteria.NAME, Order.ASC)
        val NameDesc = SortOption(Criteria.NAME, Order.DESC)
        val SizeAsc = SortOption(Criteria.SIZE, Order.ASC)
        val SizeDesc = SortOption(Criteria.SIZE, Order.DESC)
    }
}
