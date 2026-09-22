package com.hsr.railfocus.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.model.PermissionsOverview
import com.hsr.railfocus.domain.usecase.CheckPermissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Loading)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    init {
        checkPermissions()
    }
    
    fun checkPermissions() {
        viewModelScope.launch {
            try {
                val overview = checkPermissionsUseCase()
                _uiState.value = OnboardingUiState.PermissionsRequired(overview)
            } catch (e: Exception) {
                _uiState.value = OnboardingUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 标记引导完成并通知界面跳转首页
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted()
            _uiState.value = OnboardingUiState.Completed
        }
    }
}

sealed class OnboardingUiState {
    object Loading : OnboardingUiState()
    data class PermissionsRequired(val overview: PermissionsOverview) : OnboardingUiState()
    data class Error(val message: String) : OnboardingUiState()
    object Completed : OnboardingUiState()
}
