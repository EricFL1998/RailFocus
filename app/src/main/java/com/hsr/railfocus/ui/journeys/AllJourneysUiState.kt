package com.hsr.railfocus.ui.journeys

import org.maplibre.android.geometry.LatLng

/**
 * 总旅程视图状态
 *
 * @param routes 每条已完成旅程的完整线路坐标，一次旅程一条线
 * @param cameraCenter 全部线路包围盒的中心（经纬度各自取中点），用于把地图居中到所有线路
 */
data class AllJourneysUiState(
    val isLoading: Boolean = true,
    val journeyCount: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val routes: List<List<LatLng>> = emptyList(),
    val cameraCenter: LatLng? = null,
)
