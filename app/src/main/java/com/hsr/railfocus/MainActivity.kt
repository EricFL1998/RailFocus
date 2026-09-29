package com.hsr.railfocus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hsr.railfocus.ui.navigation.Screen
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.service.WeatherManager
import com.hsr.railfocus.ui.components.weather.WeatherAnimationOverlay
import com.hsr.railfocus.ui.navigation.RailFocusNavGraph
import com.hsr.railfocus.service.FocusTimerService
import com.hsr.railfocus.ui.theme.RailFocusTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var weatherManager: WeatherManager

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by userPreferencesRepository.themeMode.collectAsState(initial = "auto")
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            RailFocusTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val activeWeatherCondition by weatherManager.activeCondition.collectAsState()
                    val navController = rememberNavController()
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = currentBackStackEntry?.destination

                    // 设置、专注场景管理、总旅程视图为独立管理或历史报表页面，不覆盖动态天气粒子
                    val isWeatherExcluded = currentDestination?.let { dest ->
                        dest.hasRoute<Screen.Settings>() ||
                            dest.hasRoute<Screen.FocusTypeSettings>() ||
                            dest.hasRoute<Screen.AllJourneys>() ||
                            dest.hasRoute<Screen.Onboarding>()
                    } ?: false

                    Box(modifier = Modifier.fillMaxSize()) {
                        SharedTransitionLayout {
                            RailFocusNavGraph(
                                navController = navController,
                                sharedTransitionScope = this
                            )
                        }

                        // 全局动态天气动画浮层（下雨、下雪、雷雨、沙尘等，完全透传触摸交互）
                        // 在设置、专注场景、总旅程视图自动退隐，回到主页与旅程页面时平滑恢复
                        // 仅以真实物理时钟（19:00~06:00）作为夜间气象动效触发源，不将应用深色外观偏好与自然昼夜混淆
                        val isNight = com.hsr.railfocus.domain.model.isNightNow()
                        WeatherAnimationOverlay(
                            condition = if (!isWeatherExcluded) activeWeatherCondition else null,
                            isNight = isNight,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 服务据此判断应用是否在前台：前台完成旅程时不再弹"已到达"通知
        FocusTimerService.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        FocusTimerService.isAppInForeground = false
    }
}
