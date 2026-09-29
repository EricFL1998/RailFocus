package com.hsr.railfocus.data.remote.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 小米天气（MIUI / HyperOS 系统天气）公共 API 客户端。
 *
 * 仅用两个端点（详见 docs/Xiaomi_Weather_API_Doc.md）：
 * 1. [reverseGeo] 坐标反向归属查询：服务端按行政区边界判定坐标所属区县，
 *    返回区县级气象站 locationKey（如 weathercn:101180109 = 郑州中原区），
 *    同时由 affiliation 解析出所属地级市。这回答了"列车开到哪里算进入另一个城市"：
 *    城市边界判定交给服务端权威行政边界数据，客户端无需本地多边形。
 * 2. [fetchCurrent] 当前天气查询：只取天气现象代码（0~58）与当前气温。
 *
 * 全部方法失败时返回 null（网络错误、解析失败、非 0 errCode），
 * 由上层决定回退策略（使用旧缓存 / 保持上一次状态）。
 */
@Singleton
class XiaomiWeatherApi @Inject constructor() {

    companion object {
        private const val BASE_URL = "https://weatherapi.market.xiaomi.com/wtr-v3"
        private const val APP_KEY = "weather20151024"
        private const val SIGN = "zUFJoAR2ZVrDy1vF3D07"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) RailFocus"
        private const val TIMEOUT_MS = 10_000
    }

    /** 坐标归属查询结果 */
    data class GeoResult(
        /** 区县级气象站代码，如 weathercn:101180109 */
        val locationKey: String,
        /** 区/县名，如“中原” */
        val areaName: String,
        /** 所属地级市名，如“郑州”（affiliation 第一段） */
        val cityName: String,
        /** 所属省名，如“河南”（affiliation 第二段） */
        val provinceName: String,
    )

    /** 当前天气（仅保留需要的两个字段） */
    data class CurrentResult(
        /** 中国气象局天气现象代码 0~58 */
        val weatherCode: Int,
        /** 当前气温（摄氏度） */
        val temperatureC: Double,
    )

    /**
     * 反向归属查询：给定坐标，返回其行政归属（区县 + 地级市）与对应气象站。
     *
     * GET /wtr-v3/location/city/geo?latitude=..&longitude=..&locale=zh_cn
     * 实测响应约 160ms，国内返回 weathercn: 前缀站点，海外返回 accu: 前缀。
     */
    suspend fun reverseGeo(latitude: Double, longitude: Double): GeoResult? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE_URL/location/city/geo" +
                    "?latitude=$latitude&longitude=$longitude&locale=zh_cn"
                val array = JSONArray(httpGet(url))
                val item = array.optJSONObject(0) ?: return@runCatching null
                val locationKey = item.optString("locationKey", "")
                if (locationKey.isBlank()) return@runCatching null
                val parts = item.optString("affiliation", "")
                    .split(",")
                    .map { it.trim() }
                GeoResult(
                    locationKey = locationKey,
                    areaName = item.optString("name", ""),
                    cityName = parts.getOrElse(0) { "" },
                    provinceName = parts.getOrElse(1) { "" },
                )
            }.getOrNull()
        }

    /**
     * 查询当前天气（现象代码 + 气温）。
     *
     * GET /wtr-v3/weather/all?...&days=1
     * days=1 足以取到 current，响应仅约 11KB。
     * locationKey 可空：服务端会按坐标匹配最近站点，仍返回有效天气，
     * 但此时调用方拿不到归属名称（需要先 reverseGeo）。
     */
    suspend fun fetchCurrent(
        latitude: Double,
        longitude: Double,
        locationKey: String?,
    ): CurrentResult? = withContext(Dispatchers.IO) {
        runCatching {
            val keyParam = locationKey
                ?.takeIf { it.isNotBlank() }
                ?.let { "&locationKey=" + URLEncoder.encode(it, "UTF-8") }
                .orEmpty()
            val url = "$BASE_URL/weather/all" +
                "?latitude=$latitude&longitude=$longitude$keyParam" +
                "&days=1&appKey=$APP_KEY&sign=$SIGN&isGlobal=false&locale=zh_cn"
            val root = JSONObject(httpGet(url))
            if (root.optInt("errCode", 0) != 0) return@runCatching null
            val current = root.optJSONObject("current") ?: return@runCatching null
            // weather 与 temperature.value 均为字符串，如 "7"、"21"
            val code = current.optString("weather", "").toIntOrNull()
                ?: return@runCatching null
            val temp = current.optJSONObject("temperature")
                ?.optString("value")
                ?.toDoubleOrNull()
                ?: return@runCatching null
            CurrentResult(weatherCode = code, temperatureC = temp)
        }.getOrNull()
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val stream = if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
            val out = ByteArrayOutputStream()
            stream?.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
            return out.toString("UTF-8")
        } finally {
            conn.disconnect()
        }
    }
}
