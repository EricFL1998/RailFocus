package com.hsr.railfocus.ui.journeys

import org.maplibre.android.geometry.LatLng

/**
 * 总旅程视图状态
 *
 * @param routes 每条已完成旅程的完整线路坐标，一次旅程一条线
 * @param cameraCenter 全部线路包围盒的中心（经纬度各自取中点），用于把地图居中到所有线路
 * @param totalFocusMinutes 全部已完成专注的总分钟数
 * @param visitedStationCount 累计已打卡的独立车站数
 * @param visitedProvinceCount 累计已点亮的省级行政区数
 * @param visitedProvinces 已点亮的省级行政区列表
 */
data class AllJourneysUiState(
    val isLoading: Boolean = true,
    val journeyCount: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalFocusMinutes: Int = 0,
    val visitedStationCount: Int = 0,
    val visitedProvinceCount: Int = 0,
    val visitedProvinces: List<String> = emptyList(),
    val routes: List<List<LatLng>> = emptyList(),
    val cameraCenter: LatLng? = null,
)
