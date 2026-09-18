package com.hsr.railfocus.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsr.railfocus.domain.usecase.CheckPermissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 确定应用启动时的初始路由
 * 如果所有必需权限已授予，直接进入首页；否则显示引导页
 */
@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<Any?>(null)
    val startDestination: StateFlow<Any?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val overview = checkPermissionsUseCase()
            _startDestination.value = if (overview.allRequiredGranted) {
                Screen.Home
            } else {
                Screen.Onboarding
            }
        }
    }
}
