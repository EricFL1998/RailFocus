package com.hsr.railfocus.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 天气现象（当前实况）。
 *
 * 枚举值按中国气象局天气现象代码（0~58）归组，
 * 分组方式与小米天气客户端（MIUI / HyperOS 系统天气）一致。
 * 展示时一般使用 [label]；判断是否下雨/下雪等降水场景用 [isPrecipitation]。
 */
enum class WeatherCondition(val label: String, val isPrecipitation: Boolean) {
    CLEAR("晴", false),
    CLOUDY("多云", false),
    OVERCAST("阴", false),
    LIGHT_RAIN("小雨", true),
    MODERATE_RAIN("中雨", true),
    HEAVY_RAIN("大雨", true),
    STORM_RAIN("暴雨", true),
    THUNDER_SHOWER("雷阵雨", true),
    SLEET("雨夹雪", true),
    LIGHT_SNOW("小雪", true),
    MODERATE_SNOW("中雪", true),
    HEAVY_SNOW("大雪", true),
    STORM_SNOW("暴雪", true),
    FOG("雾", false),
    HAZE("霾", false),
    DUST("浮尘", false),
    SAND("沙尘", false),
    UNKNOWN("未知", false);

    companion object {
        /**
         * 由中国气象局天气现象代码映射为天气现象。
         * 代码分组参考小米天气：0 晴、1 多云、2 阴、3~12 雨、13~17 雪、18 雾、20/30/31 沙尘、53~58 霾等。
         */
        fun fromCnCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1 -> CLOUDY
            2 -> OVERCAST
            3, 7, 19, 21 -> LIGHT_RAIN
            4, 5 -> THUNDER_SHOWER
            6 -> SLEET
            8, 22 -> MODERATE_RAIN
            9, 23 -> HEAVY_RAIN
            10, 11, 12, 24, 25 -> STORM_RAIN
            13, 14, 26, 27 -> LIGHT_SNOW
            15, 28 -> MODERATE_SNOW
            16 -> HEAVY_SNOW
            17 -> STORM_SNOW
            18, 32, 33, 34, 49, 57, 58 -> FOG
            20, 30, 31 -> SAND
            29 -> DUST
            in 53..56 -> HAZE
            else -> UNKNOWN
        }

        /**
         * 中国气象局原始代码精确中文描述
         */
        fun cnDescription(code: Int): String = when (code) {
            0 -> "晴"
            1 -> "多云"
            2 -> "阴"
            3 -> "阵雨"
            4 -> "雷阵雨"
            5 -> "雷雨伴冰雹"
            6 -> "雨夹雪"
            7 -> "小雨"
            8 -> "中雨"
            9 -> "大雨"
            10 -> "暴雨"
            11 -> "大暴雨"
            12 -> "特大暴雨"
            13 -> "阵雪"
            14 -> "小雪"
            15 -> "中雪"
            16 -> "大雪"
            17 -> "暴雪"
            18 -> "雾"
            19 -> "冻雨"
            20 -> "沙尘暴"
            21 -> "小到中雨"
            22 -> "中到大雨"
            23 -> "大到暴雨"
            24 -> "暴雨到大暴雨"
            25 -> "特大暴雨"
            26 -> "小到中雪"
            27 -> "中到大雪"
            28 -> "大到暴雪"
            29 -> "浮尘"
            30 -> "扬沙"
            31 -> "强沙尘暴"
            32 -> "浓雾"
            33 -> "强浓雾"
            34 -> "强浓雾"
            49 -> "强浓雾"
            53 -> "轻度霾"
            54 -> "中度霾"
            55 -> "重度霾"
            56 -> "严重霾"
            57 -> "大雾"
            58 -> "特强浓雾"
            else -> "多云"
        }
    }
}

/**
 * 某个地点（行政区/网格）的当前天气。
 *
 * @param locationKey 小米天气站点代码，如 weathercn:101180109（区县级气象站）
 * @param areaName 区县级名称，如“荥阳”
 * @param cityName 所属地级市名称，如“郑州”（由 geo 接口 affiliation 第一段解析）
 * @param weatherCode 中国气象局天气现象原始代码（0~58）
 * @param temperatureC 当前气温（摄氏度）
 * @param fetchedAt 天气数据获取时间戳（毫秒）
 */
data class WeatherInfo(
    val locationKey: String,
    val areaName: String,
    val cityName: String,
    val weatherCode: Int,
    val temperatureC: Double,
    val fetchedAt: Long,
) {
    val condition: WeatherCondition
        get() = WeatherCondition.fromCnCode(weatherCode)

    /** 精确中文描述（如“大暴雨”、“雷阵雨”） */
    val detailedLabel: String
        get() = WeatherCondition.cnDescription(weatherCode)

    /** 便于直接展示的简短文案，如“小雨 21°” */
    val summary: String
        get() = "${condition.label} ${temperatureC.toInt()}°"
}

/**
 * 天气现象对应的直观符号/Emoji（☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ 🌫️ 等）
 */
val WeatherCondition.emoji: String
    get() = when (this) {
        WeatherCondition.CLEAR -> "☀️"
        WeatherCondition.CLOUDY -> "⛅"
        WeatherCondition.OVERCAST -> "☁️"
        WeatherCondition.LIGHT_RAIN, WeatherCondition.MODERATE_RAIN,
        WeatherCondition.HEAVY_RAIN, WeatherCondition.STORM_RAIN -> "🌧️"
        WeatherCondition.THUNDER_SHOWER -> "⛈️"
        WeatherCondition.SLEET -> "🌨️"
        WeatherCondition.LIGHT_SNOW, WeatherCondition.MODERATE_SNOW,
        WeatherCondition.HEAVY_SNOW, WeatherCondition.STORM_SNOW -> "❄️"
        WeatherCondition.FOG, WeatherCondition.HAZE -> "🌫️"
        WeatherCondition.DUST, WeatherCondition.SAND -> "🌪️"
        WeatherCondition.UNKNOWN -> "🌤️"
    }

/**
 * 天气现象到 Material 图标的映射（用于首页位置卡片等界面展示）
 */
fun weatherConditionIcon(condition: WeatherCondition): ImageVector = when (condition) {
    WeatherCondition.CLEAR -> Icons.Default.WbSunny
    WeatherCondition.CLOUDY -> Icons.Default.WbCloudy
    WeatherCondition.OVERCAST -> Icons.Default.Cloud
    WeatherCondition.LIGHT_RAIN, WeatherCondition.MODERATE_RAIN,
    WeatherCondition.HEAVY_RAIN, WeatherCondition.STORM_RAIN -> Icons.Default.WaterDrop
    WeatherCondition.THUNDER_SHOWER -> Icons.Default.Thunderstorm
    WeatherCondition.SLEET, WeatherCondition.LIGHT_SNOW,
    WeatherCondition.MODERATE_SNOW, WeatherCondition.HEAVY_SNOW,
    WeatherCondition.STORM_SNOW -> Icons.Default.AcUnit
    WeatherCondition.FOG -> Icons.Default.CloudQueue
    WeatherCondition.HAZE, WeatherCondition.DUST, WeatherCondition.SAND -> Icons.Default.Air
    WeatherCondition.UNKNOWN -> Icons.Default.WbSunny
}
