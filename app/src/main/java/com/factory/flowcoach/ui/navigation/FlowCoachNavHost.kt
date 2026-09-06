package com.factory.flowcoach.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.ui.ViewModelFactory
import com.factory.flowcoach.ui.history.HistoryScreen
import com.factory.flowcoach.ui.history.HistoryViewModel
import com.factory.flowcoach.ui.home.HomeScreen
import com.factory.flowcoach.ui.home.HomeViewModel
import com.factory.flowcoach.ui.paywall.PaywallScreen
import com.factory.flowcoach.ui.paywall.PaywallViewModel
import com.factory.flowcoach.ui.settings.SettingsScreen
import com.factory.flowcoach.ui.settings.SettingsViewModel

@Composable
fun FlowCoachNavHost(viewModelFactory: ViewModelFactory, premiumManager: PremiumManager) {
    val navController: NavHostController = rememberNavController()

    // The app has no onboarding flow, so first launch is the equivalent "after onboarding"
    // paywall trigger point.
    val hasSeenPaywallIntro by premiumManager.hasSeenPaywallIntro.collectAsState(initial = true)
    val isPremium by premiumManager.entitlements.collectAsState()
    LaunchedEffect(hasSeenPaywallIntro, isPremium) {
        if (!hasSeenPaywallIntro && !isPremium.isPremium) {
            navController.navigate(Screen.Paywall.route)
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = backStackEntry?.destination?.route != Screen.Paywall.route

    Scaffold(
        bottomBar = { if (showBottomBar) FlowCoachBottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
                )
            }
            composable(Screen.History.route) {
                val viewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
                )
            }
            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
                )
            }
            composable(Screen.Paywall.route) {
                val viewModel: PaywallViewModel = viewModel(factory = viewModelFactory)
                LaunchedEffect(Unit) { viewModel.markIntroSeen() }
                PaywallScreen(
                    viewModel = viewModel,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun FlowCoachBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val haptics = LocalHapticFeedback.current

    NavigationBar {
        Screen.bottomNavItems.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                // contentDescription left null: the always-visible label below already
                // provides the accessible name, avoiding a duplicate TalkBack announcement.
                icon = { Icon(screen.icon, contentDescription = null) },
                label = { Text(screen.label) }
            )
        }
    }
}
