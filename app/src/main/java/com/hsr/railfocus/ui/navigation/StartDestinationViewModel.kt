package com.hsr.railfocus.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.usecase.CheckPermissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 确定应用启动时的初始路由
 * 未完成引导则进入引导页；引导已完成但必需权限缺失时
 * 直接进入引导页的权限环节；否则直接进入首页。
 */
@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<Any?>(Screen.Home)
    val startDestination: StateFlow<Any?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val overview = checkPermissionsUseCase()
            val onboardingCompleted = userPreferencesRepository.onboardingCompleted.first()
            if (!overview.allRequiredGranted || !onboardingCompleted) {
                _startDestination.value = Screen.Onboarding(startAtPermissions = onboardingCompleted)
            }
        }
    }
}
