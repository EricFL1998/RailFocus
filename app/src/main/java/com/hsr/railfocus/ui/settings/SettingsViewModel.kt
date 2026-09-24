package com.hsr.railfocus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsr.railfocus.data.repository.AppUpdateRepository
import com.hsr.railfocus.data.repository.AppUpdateInfo
import com.hsr.railfocus.data.repository.UpdateCheckResult
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.service.DestinationCalculator
import com.hsr.railfocus.domain.usecase.ExportUserDataUseCase
import com.hsr.railfocus.domain.usecase.ImportUserDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import com.hsr.railfocus.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * 设置页面 ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val focusTypeRepository: com.hsr.railfocus.data.repository.FocusTypeRepository,
    private val journeyRepository: com.hsr.railfocus.data.repository.JourneyRepository,
    private val destinationCalculator: DestinationCalculator,
    private val appUpdateRepository: AppUpdateRepository,
    private val exportUserDataUseCase: ExportUserDataUseCase,
    private val importUserDataUseCase: ImportUserDataUseCase,
) : ViewModel() {

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    /**
     * 检查 GitHub 上的最新发布版本。
     *
     * @param silent 为 true 时（自动检查）没有新版本则静默结束，不打扰用户；
     *               手动检查会把“已是最新/失败”等结果也抛给 UI 提示。
     */
    fun checkForUpdate(currentVersion: String, silent: Boolean = false) {
        if (_updateCheckState.value == UpdateCheckState.Checking) return
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Checking
            val result = appUpdateRepository.checkForUpdate(currentVersion)
            _updateCheckState.value = when {
                silent && result !is UpdateCheckResult.UpdateAvailable -> UpdateCheckState.Idle
                else -> UpdateCheckState.Done(result)
            }
            (result as? UpdateCheckResult.UpdateAvailable)?.let { _pendingUpdate.value = it.info }
        }
    }

    fun resetUpdateCheck() {
        _updateCheckState.value = UpdateCheckState.Idle
    }

    /** 用户已处理（下载或忽略）某个版本的更新提示后，清除红点 */
    fun dismissPendingUpdate() {
        _pendingUpdate.value = null
    }

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
            initialValue = true,
        )

    val ambientSoundVolume = userPreferencesRepository.ambientSoundVolume
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 30,
        )

    fun setAmbientSoundVolume(volume: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setAmbientSoundVolume(volume)
        }
    }

    val stationAnnouncementEnabled = userPreferencesRepository.stationAnnouncementEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = true,
        )

    val keepScreenOnEnabled = userPreferencesRepository.keepScreenOnEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = true,
        )

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepScreenOnEnabled(enabled)
        }
    }

    private val _pendingUpdate = MutableStateFlow<AppUpdateInfo?>(null)

    /** 已发现但尚未处理的新版本，用于设置页“检查更新”行的版本提示 */
    val pendingUpdate: StateFlow<AppUpdateInfo?> = _pendingUpdate.asStateFlow()

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

    fun setStationAnnouncementEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setStationAnnouncementEnabled(enabled)
        }
    }

    fun addFocusType(focusType: com.hsr.railfocus.domain.model.FocusType) {
        viewModelScope.launch {
            focusTypeRepository.saveFocusType(focusType)
        }
    }

    fun deleteFocusType(id: String) {
        viewModelScope.launch {
            focusTypeRepository.deleteFocusType(id)
        }
    }

    fun exportData(outputStream: java.io.OutputStream, appVersion: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = exportUserDataUseCase.exportToStream(outputStream, appVersion)
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                onResult(true, context.getString(R.string.export_success_detail, count))
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: context.getString(R.string.settings_export_failed_short))
            }
        }
    }

    fun importData(inputStream: java.io.InputStream, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = importUserDataUseCase.importFromStream(inputStream)
            val summary = result.getOrNull()
            if (result.isSuccess && summary != null) {
                onResult(true, context.getString(R.string.import_success_detail, summary.journeys, summary.journals, summary.visitedStations))
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: context.getString(R.string.settings_import_failed_short))
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            journeyRepository.clearAllData()
            focusTypeRepository.clearAll()
            userPreferencesRepository.clearLastLocation()
            destinationCalculator.clearCache()
            userPreferencesRepository.setThemeMode("auto")
            try {
                val dir = File(context.filesDir, "journals")
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            } catch (_: Exception) {}
        }
    }
}

data class SettingsUiState(
    val themeMode: String = "auto",
)

/**
 * 更新检查 UI 状态。
 */
sealed interface UpdateCheckState {
    object Idle : UpdateCheckState
    object Checking : UpdateCheckState
    data class Done(val result: UpdateCheckResult) : UpdateCheckState
}
