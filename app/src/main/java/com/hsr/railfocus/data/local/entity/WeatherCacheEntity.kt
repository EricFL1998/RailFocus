package com.hsr.railfocus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 天气与归属地缓存（按地理网格存储）。
 *
 * 主键为 [GeoGrid] 网格键（0.1° ≈ 11km），同一网格内的多个车站共享一份数据：
 * - 归属地（locationKey/区县/市）：行政区划极少变化，缓存 7 天；
 * - 天气（weatherCode/温度）：缓存 30 分钟。
 */
@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    /** 网格键，格式 "34.7,113.6" */
    @PrimaryKey
    val gridKey: String,
    /** 区县级气象站代码，如 weathercn:101180109；geo 未成功时为空串 */
    val locationKey: String,
    /** 区/县名，如“中原” */
    val areaName: String,
    /** 地级市名，如“郑州” */
    val cityName: String,
    /** 省名，如“河南” */
    val provinceName: String,
    /** 中国气象局天气现象代码（0~58） */
    val weatherCode: Int,
    /** 当前气温（摄氏度） */
    val temperatureC: Double,
    /** 归属地反查成功时间（毫秒）；0 表示尚未成功反查 */
    @ColumnInfo(defaultValue = "0")
    val geoFetchedAt: Long,
    /** 天气获取成功时间（毫秒） */
    val weatherFetchedAt: Long,
)
