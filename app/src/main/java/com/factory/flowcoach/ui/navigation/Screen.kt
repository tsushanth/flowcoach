package com.factory.flowcoach.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Today", Icons.Filled.Home)
    data object History : Screen("history", "History", Icons.Filled.History)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object Paywall : Screen("paywall", "Premium", Icons.Filled.Home)

    companion object {
        val bottomNavItems = listOf(Home, History, Settings)
    }
}
