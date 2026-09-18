package com.hsr.railfocus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.service.DestinationCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面 ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val focusTypeRepository: com.hsr.railfocus.data.repository.FocusTypeRepository,
    private val journeyRepository: com.hsr.railfocus.data.repository.JourneyRepository,
    private val destinationCalculator: DestinationCalculator,
) : ViewModel() {

    val uiState = userPreferencesRepository.themeMode
        .map { SettingsUiState(themeMode = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SettingsUiState(),
        )

    val ambientSoundEnabled = userPreferencesRepository.ambientSoundEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false,
        )

    val focusTypes = focusTypeRepository.getFocusTypesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList(),
        )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setAmbientSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAmbientSoundEnabled(enabled)
        }
    }

    fun addFocusType(focusType: com.hsr.railfocus.ui.focus.FocusType) {
        viewModelScope.launch {
            focusTypeRepository.saveFocusType(focusType)
        }
    }

    fun deleteFocusType(id: String) {
        viewModelScope.launch {
            focusTypeRepository.deleteFocusType(id)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            journeyRepository.clearAllData()
            focusTypeRepository.clearAll()
            userPreferencesRepository.clearLastLocation()
            destinationCalculator.clearCache()
            userPreferencesRepository.setThemeMode("auto")
        }
    }
}

data class SettingsUiState(
    val themeMode: String = "auto",
)
