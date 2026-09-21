package com.hsr.railfocus.ui.journeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsr.railfocus.data.graph.RailGraph
import com.hsr.railfocus.domain.model.JourneyStatus
import com.hsr.railfocus.domain.usecase.GetJourneyHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import javax.inject.Inject

/**
 * 总旅程视图 ViewModel
 *
 * 汇总全部已完成的旅程，输出可直接交给地图渲染的线路集合。
 * 只统计已完成旅程，与数据面板的统计口径保持一致（取消的未完成车票不计入）。
 */
@HiltViewModel
class AllJourneysViewModel @Inject constructor(
    private val getJourneyHistoryUseCase: GetJourneyHistoryUseCase,
    private val railGraph: RailGraph,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AllJourneysUiState())
    val uiState: StateFlow<AllJourneysUiState> = _uiState.asStateFlow()

    init {
        getJourneyHistoryUseCase()
            .onEach { records ->
                val completed = records.filter { it.status == JourneyStatus.COMPLETED }
                val routes = withContext(Dispatchers.Default) {
                    completed.mapNotNull { record ->
                        val stations = record.path.path
                        if (stations.size > 2) {
                            // 路径已包含实际完整经过的车站序列（铁路沿线真实站点经纬度）
                            stations.map { station -> LatLng(station.lat, station.lng) }
                        } else if (stations.size == 2) {
                            // 若历史记录只存了起终两站（或直达标记），通过高铁路网图算法实时恢复两站间的真实铁路沿线物理走线
                            val realPath = railGraph.findFastestPath(stations.first().id, stations.last().id)
                            val resolvedStations = realPath?.path?.takeIf { it.size >= 2 } ?: stations
                            resolvedStations.map { station -> LatLng(station.lat, station.lng) }
                        } else {
                            null
                        }
                    }
                }
                val points = routes.flatten()
                _uiState.value = AllJourneysUiState(
                    isLoading = false,
                    journeyCount = completed.size,
                    totalDistanceKm = completed.sumOf { it.path.totalDistanceKm },
                    routes = routes,
                    // 居中到全部线路的包围盒中心：顶点的算术平均会被密集的短途线路带偏，
                    // 画面上线路会明显偏离屏幕中心。
                    cameraCenter = points.takeIf { it.isNotEmpty() }?.let { all ->
                        LatLng(
                            (all.minOf { point -> point.latitude } + all.maxOf { point -> point.latitude }) / 2,
                            (all.minOf { point -> point.longitude } + all.maxOf { point -> point.longitude }) / 2,
                        )
                    },
                )
            }
            .launchIn(viewModelScope)
    }
}
