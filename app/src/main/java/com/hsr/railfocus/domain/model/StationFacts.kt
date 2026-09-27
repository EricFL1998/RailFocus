package com.hsr.railfocus.domain.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 城市趣味知识数据类（含铁路、地标、趣闻、风物、漫游等新类别）
 */
data class StationFact(
    val city: String,
    val category: String, // 历史、地理、文化、美食、铁路、地标、趣闻、风物、漫游
    val content: String,
    val title: String = ""
)

/**
 * 城市趣味知识提供者
 * 从 assets 中的 city_facts.json 读取，为城市提供随机的趣味知识。
 *
 * 性能说明：
 * - 解析在 [Dispatchers.IO] 上执行，避免在主线程同步解析大 JSON 造成掉帧；
 * - 加载时只建立城市索引，各城市的条目在被访问时才惰性展开并缓存，
 *   避免一次性为上千个城市构建大量对象常驻内存。
 */
object StationFactsProvider {

    /** 原始城市 JSON 树（含全部城市），作为惰性展开的后备数据源 */
    private var citiesObj: JSONObject? = null

    /** 已展开城市的条目缓存，仅存放实际访问过的城市 */
    private val factsCache = mutableMapOf<String, List<StationFact>>()

    /** 记录每个城市上一次展示的知识内容，用于避免连续重复 */
    private val lastFactByCity = mutableMapOf<String, String>()

    private val loadMutex = Mutex()

    private val fallbackFacts = listOf(
        StationFact("", "铁路", "中国高铁总里程已超过 4.5 万公里，居世界第一。"),
        StationFact("", "铁路", "复兴号动车组最高运营时速可达 350 公里。"),
        StationFact("", "铁路", "高铁座位编号中没有字母 E，是为了和国际航空座位惯例一致。"),
        StationFact("", "铁路", "世界最长跨海高铁桥是杭州湾跨海铁路大桥，全长 29.2 公里。"),
        StationFact("", "铁路", "中国是世界上唯一实现时速 350 公里高铁商业运营的国家。"),
        StationFact("", "趣闻", "铁路钢轨并不是一整根铺到底，而是留有缝隙防止热胀冷缩。"),
        StationFact("", "趣闻", "京张铁路青龙桥段的人字形线路，是詹天佑为克服坡度难题设计的创举。"),
        StationFact("", "趣闻", "乘坐高铁时耳鸣，可以嚼口香糖或做吞咽动作来平衡耳压。"),
        StationFact("", "趣闻", "高铁受电弓与接触网持续摩擦，靠石墨滑板导电，需要定期更换。"),
        StationFact("", "趣闻", "西藏那曲火车站是世界上海拔最高的火车站之一，海拔超过 4500 米。"),
        StationFact("", "风物", "铁路餐车上的盒饭在不同铁路局有不同招牌菜，是流动的味蕾地图。"),
        StationFact("", "风物", "许多火车站的候车大厅都展示着当地代表性物产与非遗工艺。"),
        StationFact("", "漫游", "火车是欣赏中国地貌的绝佳方式，从平原到高原，一窗一景。"),
        StationFact("", "漫游", "夕发朝至的卧铺列车，能省下住宿费用，还能在清晨迎接目的地日出。"),
    )

    private val categoryMap = linkedMapOf(
        "history" to "历史",
        "geography" to "地理",
        "culture" to "文化",
        "food" to "美食",
        "railway" to "铁路",
        "landmark" to "地标",
        "trivia" to "趣闻",
        "specialty" to "风物",
        "travel" to "漫游"
    )

    /**
     * 加载城市趣味知识 JSON（在 IO 线程解析，不阻塞主线程）。
     * 只解析出城市索引，具体条目按需惰性展开。
     */
    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (citiesObj != null) return@withContext
        loadMutex.withLock {
            if (citiesObj != null) return@withLock
            citiesObj = try {
                val json = context.assets.open("city_facts.json").bufferedReader().use { it.readText() }
                JSONObject(json).optJSONObject("cities") ?: JSONObject()
            } catch (_: Exception) {
                JSONObject()
            }
        }
    }

    /** 惰性展开某个城市的知识条目并缓存；未加载或不存在时返回空列表 */
    private fun factsOf(city: String): List<StationFact> {
        factsCache[city]?.let { return it }
        val obj = citiesObj?.optJSONObject(city) ?: return emptyList()
        val list = categoryMap.flatMap { (jsonKey, displayLabel) ->
            val array = obj.optJSONArray(jsonKey)
            if (array != null) {
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index)
                    val content = item?.optString("content", "") ?: ""
                    if (content.isBlank()) null
                    else StationFact(
                        city = city,
                        category = displayLabel,
                        content = content,
                        title = item?.optString("title", "") ?: ""
                    )
                }
            } else {
                emptyList()
            }
        }
        factsCache[city] = list
        return list
    }

    /**
     * 常见城市名别名/后缀映射：车站或站点命名与城市知识库不一致时优先转换
     */
    private val cityAliases = mapOf(
        "上饶" to "上饶市", "宜春" to "宜春市", "万安县" to "万安",
        "仙桃" to "仙桃西", "南溪" to "南溪北", "周口" to "周口西",
        "咸阳" to "咸阳北", "大同" to "大同南", "崇左" to "崇左南",
        "平顶山" to "平顶山南", "忻州" to "忻州西", "晋城" to "晋城东",
        "景德镇" to "景德镇北", "朔州" to "朔州东", "来宾" to "来宾北",
        "松山湖" to "松山湖北", "榆林" to "榆林南", "河池" to "河池西",
        "泰州" to "泰州南", "湖州南浔" to "湖州", "眉山" to "峨眉山",
        "郑州航空港" to "郑州", "酒泉" to "酒泉南", "马鞍山" to "马鞍山东"
    )

    /** 命中某城市的条目（非空才返回） */
    private fun hit(name: String): List<StationFact>? =
        factsOf(name).takeIf { it.isNotEmpty() }

    /**
     * 根据城市名获取若干趣味知识（如果没有则返回通用铁路知识）
     */
    fun getFactsForCity(city: String): List<StationFact> {
        hit(city)?.let { return it }

        // 1) 别名映射（站点名 -> 知识库城市名）
        cityAliases[city]?.let { alias -> hit(alias)?.let { return it } }

        // 2) 去掉“市/县/区”等行政后缀再匹配
        val noSuffix = city.replace(Regex("(市|县|区)$"), "")
        if (noSuffix.isNotEmpty() && noSuffix != city) {
            hit(noSuffix)?.let { return it }
            cityAliases[noSuffix]?.let { alias -> hit(alias)?.let { return it } }
        }

        // 3) 尝试去掉“东/西/南/北/中/机场/新区”等方位及功能后缀匹配
        val trimmed = city.replace(Regex("[东南西北中]|机场|新区|开发区|高新区|经开区|工业园区|保税区区|港区区|港城|空港|海港|铁路|高铁|客运|货运|编组|枢纽|所|场$|城$|镇$|乡$|村$|街道$|区$|县$"), "")
            .trim()
        if (trimmed.isNotEmpty() && trimmed != city) {
            hit(trimmed)?.let { return it }
            cityAliases[trimmed]?.let { alias -> hit(alias)?.let { return it } }
        }

        // 4) 反向：去掉一个字的方位后缀
        if (city.length >= 3) {
            hit(city.dropLast(1))?.let { return it }
        }

        return fallbackFacts
    }

    /**
     * 随机获取一条该城市的趣味知识，尽量避免与上一次展示重复
     */
    fun randomFactForCity(city: String): StationFact {
        val facts = getFactsForCity(city)
        if (facts.isEmpty()) return fallbackFacts.random()
        val last = lastFactByCity[city]
        val candidates = if (facts.size > 1) facts.filter { it.content != last } else facts
        val picked = candidates.random()
        lastFactByCity[city] = picked.content
        return picked
    }
}
