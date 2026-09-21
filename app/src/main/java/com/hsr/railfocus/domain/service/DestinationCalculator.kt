package com.hsr.railfocus.domain.service

import com.hsr.railfocus.data.graph.RailGraph
import com.hsr.railfocus.data.local.dataaccess.VisitedStationDataAccess
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.util.appJson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * 目的地计算器
 *
 * 根据当前位置和专注时长，动态计算可以到达的目的地车站。
 *
 * 核心逻辑：
 * 1. 使用 Dijkstra（按时间权重）查找所有在时间范围内的可达站点，并直接获取每个站点的最快到达时间。
 *    - 时间是累加的：可以经过多个中间站，只要总时间 <= 用户选择的时长。
 *    - 所有边的时间均来自 TrainSpeedModel（加速→巡航→减速），避免使用 250 km/h 的平均估算。
 * 2. 仅对过滤后的站点调用 findFastestPath() 获取完整路径信息（距离、边列表），不用于重新过滤。
 * 3. 按推荐度排序返回结果：优先推荐时间最接近目标、且用户未访问过的车站。
 */
@Singleton
class DestinationCalculator @Inject constructor(
    private val railGraph: RailGraph,
    private val visitedStationDataAccess: VisitedStationDataAccess
) {
    // 简单的内存缓存：(出发站ID, 时长) -> 目的地列表
    private val cache = mutableMapOf<Pair<String, Int>, List<DestinationOption>>()

    companion object {
        /** 缓存上限，防止长时间使用后无界增长 */
        private const val MAX_CACHE_ENTRIES = 50
    }

    /**
     * 根据时长计算可达目的地。
     *
     * @param startStation 出发站
     * @param durationMinutes 专注时长（分钟）
     * @return 可达目的地列表，按推荐度排序
     */
    suspend fun calculateDestinations(
        startStation: Station,
        durationMinutes: Int
    ): List<DestinationOption> {
        val cacheKey = startStation.id to durationMinutes
        cache[cacheKey]?.let {
            android.util.Log.d("RouteCache", "Cache HIT for $cacheKey")
            return it
        }

        android.util.Log.d("RouteCache", "Cache MISS for $cacheKey, calculating...")
        // 使用单次 Dijkstra 获取所有可达路径，极大提高计算效率。
        val reachablePaths = railGraph.findAllReachablePathsWithinDuration(
            fromId = startStation.id,
            durationMin = durationMinutes
        )

        if (reachablePaths.isEmpty()) return emptyList()

        val visitedStationIds = visitedStationDataAccess.getAllVisitedStationIds().toSet()
        val results = mutableListOf<DestinationOption>()

        // 动态容差：时长的 10%，且至少 6 分钟、最多 30 分钟
        val tolerance = (durationMinutes * 0.1).toInt().coerceIn(6, 30)

        for ((stationId, pathResult) in reachablePaths) {
            val station = pathResult.path.last()
            val isVisited = stationId in visitedStationIds

            // 只保留在目标时长容差范围内的车站
            if (kotlin.math.abs(pathResult.totalDurationMin - durationMinutes) > tolerance) continue

            results.add(
                DestinationOption(
                    station = station,
                    travelTimeMinutes = pathResult.totalDurationMin,
                    distance = pathResult.totalDistanceKm,
                    recommendationScore = 0.0, // 废弃单一分数，改用多级排序
                    pathEdges = pathResult.edges,
                    pathStations = pathResult.path,
                    isVisited = isVisited
                )
            )
        }

        // 排序逻辑：
        // 1. 未访问的站在前，已访问的站在后
        // 2. 在每一组内，按时长从小到大排序 (Shorter to Longer)
        val sortedResults = results.sortedWith(
            compareBy<DestinationOption> { it.isVisited } // false (0) < true (1)
                .thenBy { it.travelTimeMinutes }
        )

        cache[cacheKey] = sortedResults
        while (cache.size > MAX_CACHE_ENTRIES) {
            // 简单淘汰策略：移除最早插入的一条
            cache.remove(cache.keys.first())
        }
        return sortedResults
    }

    /**
     * 检查指定 key 是否有缓存
     */
    fun hasCache(startStationId: String, durationMinutes: Int): Boolean {
        return cache.containsKey(startStationId to durationMinutes)
    }

    /**
     * 清除缓存（如位置发生大变动时调用）
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * 获取最佳推荐目的地。
     */
    suspend fun getBestDestination(
        startStation: Station,
        durationMinutes: Int
    ): DestinationOption? {
        val destinations = calculateDestinations(startStation, durationMinutes)
        return destinations.firstOrNull()
    }
}

/**
 * 目的地选项。
 */
@kotlinx.serialization.Serializable
data class DestinationOption(
    val station: Station,
    val travelTimeMinutes: Int,
    val distance: Double,
    val recommendationScore: Double,
    // 完整路径信息（含途经站和各段边）
    val pathEdges: List<com.hsr.railfocus.domain.model.PathResult.PathEdge> = emptyList(),
    val pathStations: List<Station> = emptyList(),
    val isVisited: Boolean = false
) {
    companion object {
        fun fromJson(json: String): DestinationOption? =
            try {
                appJson.decodeFromString<DestinationOption>(json)
            } catch (_: Exception) {
                null
            }
    }

    fun toJson(): String = appJson.encodeToString(this)
}
