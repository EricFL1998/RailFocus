package com.hsr.railfocus.domain.model

import android.content.Context
import org.json.JSONObject

/**
 * 城市趣味知识数据类
 */
data class StationFact(
    val city: String,
    val category: String, // 美食, 历史, 风景
    val content: String
)

/**
 * 城市趣味知识提供者
 * 从 assets 中的 city_facts.json 读取，为城市提供随机的趣味知识。
 */
object StationFactsProvider {

    private var factsByCity: Map<String, List<StationFact>>? = null

    private val fallbackFacts = listOf(
        StationFact("", "铁路", "中国高铁总里程已超过 4.5 万公里，居世界第一。"),
        StationFact("", "铁路", "复兴号动车组最高运营时速可达 350 公里。"),
        StationFact("", "铁路", "京沪高铁是世界上最繁忙的高铁线路之一。"),
        StationFact("", "铁路", "中国高铁单程最长可运行超过 2000 公里。"),
        StationFact("", "铁路", "高铁座位编号中没有字母 E，是为了和国际航空座位惯例一致。"),
    )

    /**
     * 加载城市趣味知识 JSON
     */
    fun load(context: Context) {
        if (factsByCity != null) return
        try {
            val json = context.assets.open("city_facts.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val citiesObj = root.optJSONObject("cities") ?: return
            
            val categoryMap = mapOf(
                "history" to "历史",
                "geography" to "地理",
                "culture" to "文化",
                "food" to "美食"
            )

            factsByCity = citiesObj.keys().asSequence().associateWith { city ->
                val cityObj = citiesObj.getJSONObject(city)
                categoryMap.flatMap { (jsonKey, displayLabel) ->
                    val array = cityObj.optJSONArray(jsonKey)
                    if (array != null) {
                        (0 until array.length()).map { index ->
                            val item = array.getJSONObject(index)
                            StationFact(city, displayLabel, item.optString("content", ""))
                        }
                    } else {
                        emptyList()
                    }
                }
            }
        } catch (_: Exception) {
            factsByCity = emptyMap()
        }
    }

    /**
     * 根据城市名获取若干趣味知识（如果没有则返回通用铁路知识）
     */
    fun getFactsForCity(city: String): List<StationFact> {
        val direct = factsByCity?.get(city)
        if (!direct.isNullOrEmpty()) return direct

        // 尝试去掉“东/西/南/北/中/机场/新区”等后缀匹配
        val trimmed = city.replace(Regex("[东南西北中]|机场|新区|开发区|高新区|经开区|工业园区|保税区区|港区区|港城|空港|海港|铁路|高铁|客运|货运|编组|枢纽|所|场$|城$|镇$|乡$|村$|街道$|区$|县$"), "")
            .trim()
        if (trimmed.isNotEmpty() && trimmed != city) {
            val parent = factsByCity?.get(trimmed)
            if (!parent.isNullOrEmpty()) return parent
        }

        return fallbackFacts
    }

    /**
     * 随机获取一条该城市的趣味知识
     */
    fun randomFactForCity(city: String): StationFact {
        val facts = getFactsForCity(city)
        return facts.randomOrNull() ?: fallbackFacts.random()
    }
}
