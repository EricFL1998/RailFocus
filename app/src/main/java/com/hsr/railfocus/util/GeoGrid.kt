package com.hsr.railfocus.util

import java.util.Locale
import kotlin.math.roundToInt

/**
 * 地理坐标网格工具。
 *
 * 把连续的经纬度坐标映射到固定大小的网格单元，
 * 用作天气/归属地缓存的键以及"位置是否发生有效变化"的去抖判断：
 * 只要列车还在同一网格内，就不需要重新请求天气或归属地。
 *
 * 网格边长 0.1 度，约合纬度方向 11 km（经度方向在中国纬度约 8~11 km）。
 * 以高铁 300 km/h 计算，穿过一个网格通常需要两分钟以上，
 * 因此网格粒度天然地平滑了行政区边界处的判定抖动。
 */
object GeoGrid {

    /**
     * 计算坐标所属网格的键，格式 "lat,lon"（各保留 1 位小数）。
     */
    fun keyOf(latitude: Double, longitude: Double): String {
        val gridLat = (latitude * 10).roundToInt() / 10.0
        val gridLon = (longitude * 10).roundToInt() / 10.0
        return String.format(Locale.US, "%.1f,%.1f", gridLat, gridLon)
    }
}
