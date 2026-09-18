package com.hsr.railfocus.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute

import com.hsr.railfocus.ui.focus.FocusSessionScreen
import com.hsr.railfocus.ui.home.HomeScreen
import com.hsr.railfocus.ui.onboarding.OnboardingScreen
import com.hsr.railfocus.ui.timeselection.TimeSelectionScreen

/**
 * 应用导航图
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RailFocusNavGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope
) {
    val viewModel: StartDestinationViewModel = hiltViewModel()
    val startDestination by viewModel.startDestination.collectAsState()

    startDestination?.let { initialRoute ->
        NavHost(
            navController = navController,
            startDestination = initialRoute,
        ) {
            // 引导页
            composable<Screen.Onboarding> {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Screen.Home) {
                            popUpTo<Screen.Onboarding> { inclusive = true }
                        }
                    }
                )
            }

            // 首页
            composable<Screen.Home> {
                HomeScreen(
                    onSettingsClick = { navController.navigate(Screen.Settings) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this
                )
            }

            // 时间选择页
            composable<Screen.TimeSelection> {
                TimeSelectionScreen(
                    onBack = { navController.popBackStack() },
                    onStartFocus = { destination ->
                        navController.navigate(Screen.FocusSession(destination.toJson())) {
                            popUpTo<Screen.TimeSelection> { inclusive = false }
                        }
                    }
                )
            }

            // 专注页
            composable<Screen.FocusSession> { backStackEntry ->
                val focusSession: Screen.FocusSession = backStackEntry.toRoute()
                FocusSessionScreen(
                    onBackHome = {
                        navController.navigate(Screen.Home) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    destinationJson = focusSession.destinationJson,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this
                )
            }

            // 设置页
            composable<Screen.Settings> {
                com.hsr.railfocus.ui.settings.SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToFocusTypeSettings = { navController.navigate(Screen.FocusTypeSettings) }
                )
            }

            // 专注场景设置页
            composable<Screen.FocusTypeSettings> {
                com.hsr.railfocus.ui.settings.FocusTypeSettingsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = hiltViewModel()
                )
            }
        }
    } ?: run {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
