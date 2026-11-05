package com.actuagt.shared.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import cafe.adriel.voyager.core.screen.Screen
import com.actuagt.shared.ui.screens.FeedScreen
import com.actuagt.shared.ui.screens.OfficialDashboardScreen
import com.actuagt.shared.ui.screens.ReportScreen

// Bottom-navigation destinations. Titles are user-facing Spanish copy.
sealed class MainTab(
    val title: String,
    val icon: ImageVector,
    val screen: Screen,
) {
    data object Feed : MainTab(
        title = "Inicio",
        icon = Icons.Filled.Home,
        screen = FeedScreen,
    )

    data object Report : MainTab(
        title = "Reportar",
        icon = Icons.Filled.AddCircle,
        screen = ReportScreen,
    )

    data object Dashboard : MainTab(
        title = "Panel",
        icon = Icons.Filled.Dashboard,
        screen = OfficialDashboardScreen,
    )

    companion object {
        val entries: List<MainTab> = listOf(Feed, Report, Dashboard)

        fun fromScreen(screen: Screen): MainTab? =
            entries.firstOrNull { it.screen == screen }
    }
}
