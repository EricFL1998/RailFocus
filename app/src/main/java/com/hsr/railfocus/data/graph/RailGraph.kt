package com.hsr.railfocus.data.graph

import com.hsr.railfocus.data.local.dataaccess.EdgeDataAccess
import com.hsr.railfocus.data.local.dataaccess.StationDataAccess
import com.hsr.railfocus.data.local.entity.toDomainModel
import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.model.journey.TrainSpeedModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 铁路图算法引擎
 * 基于 Dijkstra 最短路径和 BFS 可达性分析
 *
 * 边权重使用 TrainSpeedModel 根据距离计算真实高铁运行时间：
 * - 出站加速（0 → 最高速度）
 * - 中途巡航（接近最高速度）
 * - 到站减速（最高速度 → 0）
 * 并在除起点、终点外的每个途经站增加 1 分钟停站时间。
 */
@Singleton
class RailGraph @Inject constructor(
    private val stationDataAccess: StationDataAccess,
    private val edgeDataAccess: EdgeDataAccess,
) {

    // 邻接表缓存（懒加载）。边的运行时间在构建邻接表时一次性预计算，
    // 避免 Dijkstra 松弛过程中反复执行 200 步数值积分。
    @Volatile
    private var adjacencyList: Map<String, List<Edge>>? = null

    // 保证邻接表只初始化一次（并发调用下不会重复加载）
    private val adjacencyMutex = Mutex()

    /** 每段线路缓存距离和由 [TrainSpeedModel] 预计算的运行时间（分钟）。 */
    data class Edge(
        val toStationId: String,
        val distanceKm: Double,
        val durationMin: Int,
    )

    private val speedModel = TrainSpeedModel()

    companion object {
        /** 除起点、终点外，每个途经站停留 1 分钟。 */
        const val INTERMEDIATE_DWELL_MIN = 1
    }

    /**
     * 初始化邻接表（从数据库加载）。
     * 距离与运行时间只计算一次；若 [TrainSpeedModel] 参数调整，
     * 重建 [RailGraph] 实例（或进程重启）即可生效。
     */
    private suspend fun ensureAdjacencyList() {
        if (adjacencyList != null) return
        adjacencyMutex.withLock {
            if (adjacencyList != null) return@withLock
            adjacencyList = withContext(Dispatchers.IO) {
                val edges = edgeDataAccess.getAllEdges()
                val adjacency = mutableMapOf<String, MutableList<Edge>>()
                fun durationOf(distanceKm: Double): Int =
                    speedModel.computeTravelTimeMinutes(distanceKm.toFloat())

                for (edge in edges) {
                    val distKm = edge.distanceKm ?: 0.0

                    // 正向边
                    adjacency.getOrPut(edge.fromStationId) { mutableListOf() }
                        .add(
                            Edge(
                                toStationId = edge.toStationId,
                                distanceKm = distKm,
                                durationMin = durationOf(distKm),
                            )
                        )
                    // 反向边（铁路是无向图，A↔B）
                    adjacency.getOrPut(edge.toStationId) { mutableListOf() }
                        .add(
                            Edge(
                                toStationId = edge.fromStationId,
                                distanceKm = distKm,
                                durationMin = durationOf(distKm),
                            )
                        )
                }

                adjacency
            }
        }
    }

    /**
     * Dijkstra 最短路径算法（按距离）
     * @param fromId 起点站 ID
     * @param toId 终点站 ID
     * @return PathResult 包含路径、总距离、总时间
     */
    suspend fun findShortestPath(fromId: String, toId: String): PathResult? {
        ensureAdjacencyList()

        val adjList = adjacencyList ?: return null
        if ((fromId !in adjList) || (toId !in adjList)) return null

        // Dijkstra 核心数据结构
        val distances = mutableMapOf(fromId to 0.0)
        val previous = mutableMapOf<String, String>()
        val visited = mutableSetOf<String>()

        // 优先队列：按距离排序
        val queue = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        queue.add(fromId to 0.0)

        while (queue.isNotEmpty()) {
            val (currentId, currentDist) = queue.poll() ?: break

            if (currentId == toId) break // 找到目标
            if (currentId in visited) continue

            visited.add(currentId)

            // 松弛操作
            adjList[currentId]?.forEach { edge ->
                if (edge.toStationId !in visited) {
                    val newDist = currentDist + edge.distanceKm
                    val oldDist = (distances[edge.toStationId] ?: Double.MAX_VALUE)

                    if (newDist < oldDist) {
                        distances[edge.toStationId] = newDist
                        previous[edge.toStationId] = currentId
                        queue.add(edge.toStationId to newDist)
                    }
                }
            }
        }

        // 未找到路径
        if (toId !in distances) return null

        // 回溯路径
        val path = buildList {
            var current = toId
            while (current != fromId) {
                add(0, current)
                current = previous[current] ?: return null
            }
            add(0, fromId)
        }

        return buildPathResult(adjList, path)
    }

    /**
     * BFS 查找 N 步范围内可达的所有站点
     * @param fromId 起点站 ID
     * @param maxHops 最大跳数
     * @return 可达站点列表（带跳数）
     */
    suspend fun findReachableStations(
        fromId: String,
        maxHops: Int,
    ): List<Pair<Station, Int>> {
        ensureAdjacencyList()

        val adjList = adjacencyList ?: return emptyList()
        if (fromId !in adjList) return emptyList()

        val result = mutableListOf<Pair<String, Int>>()
        val visited = mutableSetOf(fromId)
        var currentLevel = listOf(fromId)

        // BFS 逐层遍历
        for (hop in 1..maxHops) {
            val nextLevel = mutableListOf<String>()

            for (stationId in currentLevel) {
                adjList[stationId]?.forEach { edge ->
                    if (edge.toStationId !in visited) {
                        visited.add(edge.toStationId)
                        nextLevel.add(edge.toStationId)
                        result.add(edge.toStationId to hop)
                    }
                }
            }

            if (nextLevel.isEmpty()) break
            currentLevel = nextLevel
        }

        // 加载站点详情
        val stationIds = result.map { it.first }
        val stations = withContext(Dispatchers.IO) {
            stationDataAccess.getStationsByIds(stationIds)
                .asSequence()
                .map { it.toDomainModel() }
                .associateBy { it.id }
        }

        return result.mapNotNull { (id, hop) ->
            stations[id]?.let { it to hop }
        }
    }

    /**
     * 根据专注时长查找可达站点
     * @param fromId 起点站 ID
     * @param durationMin 专注时长（分钟）
     * @param tolerance 容差（默认 ±10 分钟）
     * @return 时间范围内可达的站点列表
     */
    suspend fun findStationsByDuration(
        fromId: String,
        durationMin: Int,
        tolerance: Int = 10,
    ): List<Station> {
        ensureAdjacencyList()

        val adjList = adjacencyList ?: return emptyList()
        if (fromId !in adjList) return emptyList()

        val minDuration = durationMin - tolerance
        val maxDuration = durationMin + tolerance

        // Dijkstra 按时间查找
        val durations = mutableMapOf(fromId to 0)
        val visited = mutableSetOf<String>()
        val result = mutableSetOf<String>()

        val queue = PriorityQueue<Pair<String, Int>>(compareBy { it.second })
        queue.add(fromId to 0)

        while (queue.isNotEmpty()) {
            val (currentId, currentDuration) = queue.poll() ?: break

            if (currentDuration > maxDuration) break
            if (currentId in visited) continue

            visited.add(currentId)

            // 在目标时间范围内（不含起点本身）
            if (currentId != fromId && currentDuration in minDuration..maxDuration) {
                result.add(currentId)
            }

            adjList[currentId]?.forEach { edge ->
                if (edge.toStationId !in visited) {
                    val dwell = if (currentId == fromId) 0 else INTERMEDIATE_DWELL_MIN
                    val newDuration = currentDuration + edge.durationMin + dwell
                    val oldDuration = (durations[edge.toStationId] ?: Int.MAX_VALUE)

                    if (newDuration < oldDuration && newDuration <= maxDuration) {
                        durations[edge.toStationId] = newDuration
                        queue.add(edge.toStationId to newDuration)
                    }
                }
            }
        }

        // 没有可达站点时直接返回，避免对空列表发起数据库查询
        if (result.isEmpty()) return emptyList()

        // 加载站点详情
        return withContext(Dispatchers.IO) {
            stationDataAccess.getStationsByIds(result.toList())
                .asSequence()
                .map { it.toDomainModel() }
                .toList()
        }
    }

    /**
     * 查找指定时间内的所有可达站点（不含起点站），并返回每个站点的最快到达时间。
     * 使用 Dijkstra 算法，按行程时间查找所有在时间范围内的站点。
     *
     * 时间由 [TrainSpeedModel] 统一计算，并包含途经站停站时间。
     *
     * @param fromId 起点站 ID
     * @param durationMin 最大行程时间（分钟）
     * @return 站点 ID 到最快到达时间（分钟）的映射
     */
    suspend fun findStationsWithinDurationWithTime(
        fromId: String,
        durationMin: Int,
    ): Map<String, Int> {
        ensureAdjacencyList()

        val adjList = adjacencyList ?: return emptyMap()
        if (fromId !in adjList) return emptyMap()

        val durations = mutableMapOf(fromId to 0)
        val visited = mutableSetOf<String>()
        val result = mutableMapOf<String, Int>()

        val queue = PriorityQueue<Pair<String, Int>>(compareBy { it.second })
        queue.add(fromId to 0)

        while (queue.isNotEmpty()) {
            val (currentId, currentDuration) = queue.poll() ?: break

            if (currentDuration > durationMin) break
            if (currentId in visited) continue

            visited.add(currentId)

            if (currentId != fromId) {
                result[currentId] = currentDuration
            }

            adjList[currentId]?.forEach { edge ->
                if (edge.toStationId !in visited) {
                    val dwell = if (currentId == fromId) 0 else INTERMEDIATE_DWELL_MIN
                    val newDuration = currentDuration + edge.durationMin + dwell
                    val oldDuration = (durations[edge.toStationId] ?: Int.MAX_VALUE)

                    if (newDuration < oldDuration && newDuration <= durationMin) {
                        durations[edge.toStationId] = newDuration
                        queue.add(edge.toStationId to newDuration)
                    }
                }
            }
        }

        return result
    }

    /**
     * 查找指定时间内的所有可达站点，并返回每个站点的完整 PathResult。
     * 
     * 核心优化：在单次 Dijkstra 遍历中记录所有前驱节点，从而在 O(V+E) 时间内获取所有路径。
     * 这比多次调用 findFastestPath() 效率高得多。
     *
     * @param fromId 起点站 ID
     * @param durationMin 最大行程时间（分钟）
     * @param toleranceMin 额外搜索的时间容差（分钟），用于发现稍微超过目标时长的站点
     * @return 站点 ID 到 PathResult 的映射
     */
    suspend fun findAllReachablePathsWithinDuration(
        fromId: String,
        durationMin: Int,
        toleranceMin: Int = 30, // 增加搜索冗余，确保能搜到稍微远一点的站
    ): Map<String, PathResult> {
        ensureAdjacencyList()

        val adjList = adjacencyList ?: return emptyMap()
        if (fromId !in adjList) return emptyMap()

        val searchLimit = durationMin + toleranceMin
        val durations = mutableMapOf(fromId to 0)
        val previous = mutableMapOf<String, String>()
        val visited = mutableSetOf<String>()

        val queue = PriorityQueue<Pair<String, Int>>(compareBy { it.second })
        queue.add(fromId to 0)

        while (queue.isNotEmpty()) {
            val (currentId, currentDuration) = queue.poll() ?: break

            if (currentDuration > searchLimit) break
            if (currentId in visited) continue

            visited.add(currentId)

            adjList[currentId]?.forEach { edge ->
                if (edge.toStationId !in visited) {
                    val dwell = if (currentId == fromId) 0 else INTERMEDIATE_DWELL_MIN
                    val newDuration = currentDuration + edge.durationMin + dwell
                    val oldDuration = (durations[edge.toStationId] ?: Int.MAX_VALUE)

                    if (newDuration < oldDuration && newDuration <= searchLimit) {
                        durations[edge.toStationId] = newDuration
                        previous[edge.toStationId] = currentId
                        queue.add(edge.toStationId to newDuration)
                    }
                }
            }
        }

        // 批量加载所有可达站点的详情
        val reachableIds = durations.keys.toList()
        val stationMap = mutableMapOf<String, Station>()
        
        // 分批查询，防止 SQLite IN 子句限制 (999个参数)
        withContext(Dispatchers.IO) {
            reachableIds.chunked(900).forEach { chunk ->
                stationDataAccess.getStationsByIds(chunk)
                    .map { it.toDomainModel() }
                    .forEach { stationMap[it.id] = it }
            }
        }

        // 为每个站点回溯路径并构建 PathResult
        val results = mutableMapOf<String, PathResult>()
        for (targetId in reachableIds) {
            if (targetId == fromId) continue

            val pathIds = mutableListOf<String>()
            var curr: String? = targetId
            while (curr != null) {
                pathIds.add(0, curr)
                curr = if (curr == fromId) null else previous[curr]
            }

            if (pathIds.firstOrNull() != fromId) continue

            val stations = pathIds.mapNotNull { stationMap[it] }
            if (stations.size != pathIds.size) continue // 确保路径完整

            var totalDistance = 0.0
            val edges = buildList {
                for (i in 0 until pathIds.size - 1) {
                    val from = stations[i]
                    val to = stations[i + 1]
                    val edge = adjList[pathIds[i]]?.find { it.toStationId == pathIds[i + 1] }
                    if (edge != null) {
                        totalDistance += edge.distanceKm
                        add(
                            PathResult.PathEdge(
                                from = from,
                                to = to,
                        durationMin = edge.durationMin,
                                lineName = "",
                                distanceKm = edge.distanceKm,
                            )
                        )
                    }
                }
            }

            results[targetId] = PathResult(
                path = stations,
                totalDistanceKm = totalDistance,
                totalDurationMin = durations[targetId] ?: 0,
                edges = edges,
            )
        }

        return results
    }

    /**
     * Dijkstra 最短路径算法（按时间）
     *
     * 与 findShortestPath 不同，本方法使用行程时间作为权重，
     * 找到时间最短的路径（而非距离最短）。
     *
     * 时间包括：
     * - 每段线路的真实运行时间（加速/巡航/减速）
     * - 除起点、终点外每个途经站的 1 分钟停站时间
     *
     * @param fromId 起点站 ID
     * @param toId 终点站 ID
     * @return PathResult 包含最快路径、总距离、总时间
     */
    suspend fun findFastestPath(fromId: String, toId: String): PathResult? {
        ensureAdjacencyList()

        val adjList = adjacencyList ?: return null
        if ((fromId !in adjList) || (toId !in adjList)) return null

        // Dijkstra 核心数据结构 — 按时间排序
        val durations = mutableMapOf(fromId to 0)
        val previous = mutableMapOf<String, String>()
        val visited = mutableSetOf<String>()

        // 优先队列：按累计时间排序
        val queue = PriorityQueue<Pair<String, Int>>(compareBy { it.second })
        queue.add(fromId to 0)

        while (queue.isNotEmpty()) {
            val (currentId, currentDuration) = queue.poll() ?: break

            if (currentId == toId) break
            if (currentId in visited) continue

            visited.add(currentId)

            // 松弛操作：按时间累加
            adjList[currentId]?.forEach { edge ->
                if (edge.toStationId !in visited) {
                    val dwell = if (currentId == fromId) 0 else INTERMEDIATE_DWELL_MIN
                    val newDuration = currentDuration + edge.durationMin + dwell
                    val oldDuration = (durations[edge.toStationId] ?: Int.MAX_VALUE)

                    if (newDuration < oldDuration) {
                        durations[edge.toStationId] = newDuration
                        previous[edge.toStationId] = currentId
                        queue.add(edge.toStationId to newDuration)
                    }
                }
            }
        }

        // 未找到路径
        if (toId !in durations) return null

        // 回溯路径
        val path = buildList {
            var current = toId
            while (current != fromId) {
                add(0, current)
                current = previous[current] ?: return null
            }
            add(0, fromId)
        }

        return buildPathResult(adjList, path)
    }

    /**
     * 根据回溯路径构建 [PathResult]。
     * 同时计算总距离、总时间（含途经站停站时间）和每段边信息。
     */
    private suspend fun buildPathResult(
        adjList: Map<String, List<Edge>>,
        path: List<String>,
    ): PathResult {
        // 计算总时间和距离
        var totalDistance = 0.0
        var totalDuration = 0

        for (i in 0 until path.size - 1) {
            val from = path[i]
            val to = path[i + 1]
            val edge = adjList[from]?.find { it.toStationId == to }
            if (edge != null) {
                totalDistance += edge.distanceKm
                totalDuration += edge.durationMin
                if (i > 0) {
                    totalDuration += INTERMEDIATE_DWELL_MIN
                }
            }
        }

        // 加载站点详情，并按 path 顺序重新排列
        val stationMap = withContext(Dispatchers.IO) {
            stationDataAccess.getStationsByIds(path)
                .asSequence()
                .map { it.toDomainModel() }
                .associateBy { it.id }
        }
        val stations = path.mapNotNull { stationMap[it] }

        // 构建边信息
        val edges = buildList {
            for (i in 0 until path.size - 1) {
                val from = stations[i]
                val to = stations[i + 1]
                val edge = adjList[path[i]]?.find { it.toStationId == path[i + 1] }
                if (edge != null) {
                        add(
                            PathResult.PathEdge(
                                from = from,
                                to = to,
                            durationMin = edge.durationMin,
                                lineName = "",
                                distanceKm = edge.distanceKm,
                            )
                        )
                }
            }
        }

        return PathResult(
            path = stations,
            totalDistanceKm = totalDistance,
            totalDurationMin = totalDuration,
            edges = edges,
        )
    }
}
