package com.bobbyesp.docucraft.presentation.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.bobbyesp.docucraft.R
import dagger.hilt.android.qualifiers.ApplicationContext

sealed class Route(
    val route: String,
    @StringRes val title: Int? = null,
    val icon: ImageVector? = null,
) {
    data object MainHost : Route("main_host")

    data object DocucraftNavigator : Route(
        "docucraft_navigator",
        title = R.string.home,
        icon = Icons.Rounded.Home
    ) {
        data object Home :
            Route(
                "home",
                title = R.string.home,
                icon = Icons.Rounded.Home
            ) {
        }
    }
}

val routesToNavigate = listOf(
    Route.DocucraftNavigator,
)

fun Route.getTitle(@ApplicationContext context: Context): String? {
    return title?.let { context.getString(it) }
}

enum class NavArgs(val key: String) {

}