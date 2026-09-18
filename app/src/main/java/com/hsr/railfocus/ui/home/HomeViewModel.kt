package com.hsr.railfocus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsr.railfocus.data.location.LocationManager
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val stationRepository: StationRepository,
    private val locationManager: LocationManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val focusTypeRepository: com.hsr.railfocus.data.repository.FocusTypeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val focusTypes = focusTypeRepository.getFocusTypesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    init {
        checkLocationPermission()
        loadNearbyStations()
    }

    private fun loadNearbyStations() {
        viewModelScope.launch {
            try {
                val allStations = stationRepository.getAllStations()

                // 1. 尝试获取上次保存的位置
                val savedLocation = preferencesRepository.lastLocation.first()
                if (savedLocation != null) {
                    val savedStation = allStations.find { it.id == savedLocation.stationId }
                    if (savedStation != null) {
                        _uiState.value = _uiState.value.copy(
                            currentStation = savedStation,
                            currentLocation = LatLng(savedStation.lat, savedStation.lng),
                            currentStationName = savedStation.city,
                            currentStationDisplayName = savedStation.name,
                            greeting = getGreeting(),
                            isLoading = false
                        )
                        
                        // 移除这里的 refreshLocation 调用，尊重用户“只有第一次使用是gps定位”的需求
                        // if (locationManager.hasLocationPermission()) {
                        //     refreshLocation(allStations)
                        // }
                        return@launch
                    }
                }

                // 2. 如果没有保存的位置但有权限，尝试实时定位 (这就是“第一次使用”的情况)
                if (locationManager.hasLocationPermission()) {
                    val locationFetched = refreshLocation(allStations)
                    if (locationFetched) {
                        _uiState.value = _uiState.value.copy(
                            greeting = getGreeting(),
                            isLoading = false
                        )
                        return@launch
                    }
                }

                // 3. 默认兜底使用南京南
                val currentStation = allStations.find { it.id == "南京南" }
                    ?: Station.DEFAULT

                _uiState.value = _uiState.value.copy(
                    currentStation = currentStation,
                    currentLocation = LatLng(currentStation.lat, currentStation.lng),
                    currentStationName = currentStation.city,
                    currentStationDisplayName = currentStation.name,
                    greeting = getGreeting(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * 检查是否需要请求位置权限
     */
    private fun checkLocationPermission() {
        val hasPermission = locationManager.hasLocationPermission()
        _uiState.value = _uiState.value.copy(
            needsLocationPermission = !hasPermission
        )
    }

    /**
     * 权限已授予后，尝试获取GPS位置
     */
    fun onPermissionGranted() {
        _uiState.value = _uiState.value.copy(needsLocationPermission = false)
        viewModelScope.launch {
            val allStations = stationRepository.getAllStations()
            refreshLocation(allStations)
        }
    }

    /**
     * 核心定位逻辑：获取 GPS 并更新 UI 状态
     * @return 是否成功获取并更新了位置
     */
    private suspend fun refreshLocation(allStations: List<Station>): Boolean {
        return try {
            val preferredProvider = preferencesRepository.preferredLocationProvider.first()
            val result = locationManager.getCurrentLocation(preferredProvider)
            
            if (result != null) {
                val location = result.location
                // 记录这次成功的提供商，但不覆盖已保存的 lastLocation（避免覆盖上次旅程目的地）
                preferencesRepository.setPreferredLocationProvider(result.provider)

                val nearestStation = findNearestStation(
                    location.latitude,
                    location.longitude,
                    allStations,
                )
                nearestStation?.let { station ->
                    // 仅在未保存过位置时（首次使用），才用 GPS 结果更新 lastLocation
                    val hasSavedLocation = preferencesRepository.lastLocation.first() != null
                    if (!hasSavedLocation) {
                        preferencesRepository.saveLastLocation(
                            com.hsr.railfocus.data.preferences.SavedLocation(
                                latitude = station.lat,
                                longitude = station.lng,
                                stationId = station.id,
                                stationName = station.name,
                                city = station.city
                            )
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        currentStation = station,
                        currentLocation = LatLng(station.lat, station.lng),
                        currentStationName = station.city,
                        currentStationDisplayName = station.name,
                    )
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 找到最近的车站
     */
    private fun findNearestStation(
        lat: Double,
        lng: Double,
        stations: List<Station>
    ): Station? {
        return stations.minByOrNull { station ->
            haversineDistance(lat, lng, station.lat, station.lng)
        }
    }

    /**
     * Haversine 公式计算两点距离（公里）
     */
    private fun haversineDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val r = 6371.0 // 地球半径（公里）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = (kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2))
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    /**
     * 生成问候语
     */
    private fun getGreeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 6..11 -> "home_greeting_morning"
            in 12..17 -> "home_greeting_afternoon"
            else -> "home_greeting_evening"
        }
    }
}

data class HomeUiState(
    val currentStation: Station = Station.DEFAULT,
    val currentLocation: LatLng = LatLng(31.9728, 118.8047),
    val currentStationName: String = "南京",
    val currentStationDisplayName: String = "南京南站",
    val greeting: String = "你好",
    val isLoading: Boolean = true,
    val needsLocationPermission: Boolean = false,
    val error: String? = null
)
