package com.hsr.railfocus.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
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
                val route = it.toRoute<Screen.Onboarding>()
                OnboardingScreen(
                    startAtPermissions = route.startAtPermissions,
                    onComplete = {
                        navController.navigate(Screen.Home) {
                            popUpTo<Screen.Onboarding> { inclusive = true }
                        }
                    }
                )
            }

            // 首页
            composable<Screen.Home>(
                // 二级页面从右侧推进来时，首页轻微左移淡出；返回时再滑回来
                exitTransition = {
                    if (targetState.destination.isSlidePage()) pagePushExit() else null
                },
                popEnterTransition = {
                    if (initialState.destination.isSlidePage()) pagePopEnter() else null
                },
            ) {
                HomeScreen(
                    onSettingsClick = { navController.navigate(Screen.Settings) },
                    onAllJourneysClick = { navController.navigate(Screen.AllJourneys) },
                    sharedTransitionScope = sharedTransitionScope
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
            composable<Screen.Settings>(
                enterTransition = { pagePushEnter() },
                exitTransition = { pagePushExit() },
                popEnterTransition = { pagePopEnter() },
                popExitTransition = { pagePopExit() },
            ) {
                com.hsr.railfocus.ui.settings.SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToFocusTypeSettings = { navController.navigate(Screen.FocusTypeSettings) }
                )
            }

            // 总旅程视图（全部已完成线路）
            composable<Screen.AllJourneys>(
                enterTransition = { pagePushEnter() },
                exitTransition = { pagePushExit() },
                popEnterTransition = { pagePopEnter() },
                popExitTransition = { pagePopExit() },
            ) {
                com.hsr.railfocus.ui.journeys.AllJourneysScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // 专注场景设置页
            composable<Screen.FocusTypeSettings>(
                enterTransition = { pagePushEnter() },
                exitTransition = { pagePushExit() },
                popEnterTransition = { pagePopEnter() },
                popExitTransition = { pagePopExit() },
            ) {
                com.hsr.railfocus.ui.settings.FocusTypeSettingsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = hiltViewModel()
                )
            }
        }
    } ?: run {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
        )
    }
}

/**
 * 二级页面（设置 / 专注场景设置 / 总旅程）之间共用的横向推入动画时长。
 * 与 App 内其它过渡保持同一节奏，用同一条缓动曲线，短促不拖沓。
 */
private const val PAGE_SLIDE_DURATION_MS = 320

/** 底层页面让位时退让的比例：只往旁边挪一点，做出层叠推进的纵深感 */
private const val PAGE_SLIDE_BACK_FRACTION = 4

/** 新页面从右侧滑入 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pagePushEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(PAGE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
    ) { it } + fadeIn(tween(PAGE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing))

/** 当前页面往左退让并淡出，让新页面盖上来 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pagePushExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(PAGE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
    ) { -it / PAGE_SLIDE_BACK_FRACTION } +
        fadeOut(tween(PAGE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing))

/** 返回时下层页面从左侧滑回原位 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pagePopEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(PAGE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
    ) { -it / PAGE_SLIDE_BACK_FRACTION } +
        fadeIn(tween(PAGE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing))

/** 返回时当前页面整体向右滑出 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pagePopExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(PAGE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
    ) { it }

/** 当前页面是否属于需要横向推入动画的二级页面 */
private fun NavDestination.isSlidePage(): Boolean =
    hasRoute<Screen.Settings>() ||
        hasRoute<Screen.AllJourneys>() ||
        hasRoute<Screen.FocusTypeSettings>()
