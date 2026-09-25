package com.hsr.railfocus.ui.timeselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsr.railfocus.data.repository.StationRepository
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.service.DestinationCalculator
import com.hsr.railfocus.domain.service.DestinationOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 时间选择页面 ViewModel
 * 
 * 管理：
 * - 当前出发站
 * - 选择的时长
 * - 可达目的地列表
 * - 车站搜索
 */
@HiltViewModel
class TimeSelectionViewModel @Inject constructor(
    private val destinationCalculator: DestinationCalculator,
    private val stationRepository: StationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimeSelectionUiState())
    val uiState: StateFlow<TimeSelectionUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Station>>(emptyList())
    val searchResults: StateFlow<List<Station>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null
    private var calculationJob: Job? = null

    // 全站列表缓存：车站数据是只读的，避免每次打开搜索/清空查询都重新查库排序
    private var allStationsCache: List<Station>? = null

    init {
        // 延迟全站搜索列表的重载，仅在用户唤起搜索时按需加载，去除启动时全表排序的CPU阻塞
        _uiState.update { it.copy(selectedDuration = it.minDuration) }
    }

    /**
     * 初始化出发站
     */
    fun initializeWithStation(station: Station) {
        setStartStation(station)
    }

    /**
     * 设置出发站
     */
    fun setStartStation(station: Station) {
        val currentDuration = _uiState.value.selectedDuration
        _uiState.update {
            it.copy(
                startStation = station,
                destinations = emptyList(),
                isCalculating = (currentDuration != null),
            )
        }

        currentDuration?.let { duration ->
            onDurationSelected(duration)
        }
    }

    /**
     * 更新出发站（从搜索选择或重新进入路线选择）
     * 重置时间到最小，清除旧目的地和路径，并重新计算
     */
    fun updateStartStation(station: Station) {
        // 如果是同一个车站且已经有数据，直接返回
        if (_uiState.value.startStation.id == station.id && _uiState.value.destinations.isNotEmpty()) {
            return
        }

        _uiState.update {
            it.copy(
                startStation = station,
                selectedDuration = it.minDuration,
                selectedDestination = null,
                selectedDestinationIndex = 0,
                destinations = emptyList(),
                isCalculating = false // 初始化为 false，由 onDurationSelected 决定是否显示
            )
        }
        _searchQuery.value = ""

        onDurationSelected(_uiState.value.minDuration)
    }

    /**
     * 选择时长后，计算可达目的地，并默认选中旅行时长最接近所选时间的目的地
     */
    fun onDurationSelected(duration: Int) {
        calculationJob?.cancel()
        calculationJob = null

        // 立即更新 UI 中的选中数值，但不清除目的地列表，防止地图变白。
        // 同时不要重置 selectedDestinationIndex：计算期间保持当前选中项，
        // 否则新结果出来前会先跳到第一个，再跳到最接近时间的那个，造成闪烁。
        _uiState.update {
            it.copy(
                selectedDuration = duration,
                // selectedDestination = null, // 关键：不要在这里清除，保持旧路径直到新结果出来
                // isCalculating = true // 关键：不要立即触发 loading 导致的 UI 震荡
            )
        }

        calculationJob = viewModelScope.launch {
            try {
                val startId = _uiState.value.startStation.id
                val isCached = destinationCalculator.hasCache(startId, duration)

                if (!isCached) {
                    // 如果没缓存，稍微等一下再显示 Loading，防止闪烁
                    delay(200)
                    _uiState.update { it.copy(isCalculating = true) }
                }

                val destinations = destinationCalculator.calculateDestinations(
                    startStation = _uiState.value.startStation,
                    durationMinutes = duration
                )

                // 默认选中旅行时长最接近所选时间的目的地（并列时取列表中较早的一个）
                val closestIndex = destinations.indices.minByOrNull { index ->
                    kotlin.math.abs(destinations[index].travelTimeMinutes - duration)
                } ?: 0

                _uiState.update {
                    it.copy(
                        destinations = destinations,
                        selectedDestinationIndex = closestIndex,
                        selectedDestination = destinations.getOrNull(closestIndex),
                        isCalculating = false
                    )
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                _uiState.update { it.copy(isCalculating = false, error = e.message) }
            }
        }
    }

    /**
     * 选择目的地（按索引）
     */
    fun onDestinationSelected(index: Int) {
        _uiState.update {
            it.copy(
                selectedDestinationIndex = index,
                selectedDestination = it.destinations.getOrNull(index)
            )
        }
    }

    /**
     * 搜索车站（带防抖）
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // 防抖 300ms
            if (query.isBlank()) {
                // 空搜索时显示全部车站
                _searchResults.value = cachedAllStations()
            } else {
                val results = stationRepository.searchStations(query)
                _searchResults.value = results.sortedWith(compareBy({ it.tier }, { it.name }))
            }
        }
    }

    /**
     * 显示/隐藏搜索面板
     */
    fun setShowStationSearch(show: Boolean) {
        _uiState.update { it.copy(showStationSearch = show) }
        if (show) {
            // 打开时重置搜索，并加载全部车站
            _searchQuery.value = ""
            viewModelScope.launch {
                _searchResults.value = cachedAllStations()
            }
        }
    }

    private suspend fun cachedAllStations(): List<Station> {
        return allStationsCache ?: stationRepository.getAllStations()
            .sortedWith(compareBy({ it.tier }, { it.name }))
            .also { allStationsCache = it }
    }

    /**
     * 清除缓存的目的地数据
     */
    fun clearCache() {
        calculationJob?.cancel()
        calculationJob = null
        _uiState.update {
            it.copy(
                destinations = emptyList(),
                selectedDestination = null,
                isCalculating = false
            )
        }
    }

    /**
     * 重置到指定车站（通常是 GPS 站）并重置所有选择状态
     */
    fun resetToStation(station: Station) {
        _uiState.update {
            it.copy(
                startStation = station,
                selectedDuration = it.minDuration,
                destinations = emptyList(),
                selectedDestination = null,
                selectedDestinationIndex = 0,
                isCalculating = true
            )
        }
        _searchQuery.value = ""
        onDurationSelected(_uiState.value.minDuration)
    }

    /**
     * 重置到最小可选时间
     */
    fun resetToMinimum() {
        val min = _uiState.value.minDuration
        onDurationSelected(min)
    }
}

data class TimeSelectionUiState(
    val startStation: Station = Station.DEFAULT,
    val selectedDuration: Int? = null,
    val minDuration: Int = 15,
    val maxDuration: Int = 300,
    val destinations: List<DestinationOption> = emptyList(),
    val isCalculating: Boolean = false,
    val showStationSearch: Boolean = false,
    val selectedDestinationIndex: Int = 0,
    val selectedDestination: DestinationOption? = null,
    val error: String? = null
)
