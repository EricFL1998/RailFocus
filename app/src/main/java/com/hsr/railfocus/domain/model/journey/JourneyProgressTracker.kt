package com.hsr.railfocus.domain.model.journey

import com.hsr.railfocus.domain.model.PathResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 旅程进度追踪器
 * 
 * 负责根据时间计算火车在路径上的精确位置。
 *
 * 将旅程视为一条时间轴：每段线路有真实运行时间（加速→巡航→减速），
 * 到达每个中间站后停靠 1-2 分钟（见 [TrainSpeedModel.dwellMinutesFor]），
 * 停靠期间速度为 0，与路线时间计算保持一致。
 */
@Singleton
class JourneyProgressTracker @Inject constructor() {
    
    private val speedModel = TrainSpeedModel()
    
    /**
     * 计算当前旅程进度
     * 
     * @param path 完整的站点路径
     * @param elapsedSeconds 已经过的时间（秒）
     * @param totalSeconds 总旅程时间（秒）
     * @return 当前进度状态
     */
    fun calculateProgress(
        path: PathResult,
        elapsedSeconds: Int,
        totalSeconds: Int,
    ): JourneyProgress {
        // 边界检查
        if (path.path.size < 2) {
            return createEmptyProgress()
        }
        
        if (elapsedSeconds <= 0) {
            return createInitialProgress(path)
        }
        
        if (elapsedSeconds >= totalSeconds) {
            return createCompletedProgress(path)
        }
        
        val totalDistance = path.totalDistanceKm.toFloat()
        val timeRatio = (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        val segmentCount = path.path.size - 1

        // 各段行驶时间（秒），与路线时间计算使用同一数据源
        val segmentTimes = resolveSegmentTimes(path, totalSeconds)

        var remaining = elapsedSeconds
        var accumulatedDistance = 0f

        for (i in 0 until segmentCount) {
            val segmentDistance = getSegmentDistance(path, i)
            val segmentTime = segmentTimes[i]

            // 正在第 i 段行驶
            if (remaining < segmentTime) {
                val fraction = if (segmentTime > 0) remaining.toFloat() / segmentTime else 0f
                val distanceInSegment = fraction * segmentDistance
                val currentSpeed = speedModel.calculateSpeed(
                    distanceInSegment = distanceInSegment,
                    segmentTotalDistance = segmentDistance,
                    elapsedSeconds = elapsedSeconds,
                )
                val isApproaching = speedModel.isApproachingStation(
                    distanceInSegment = distanceInSegment,
                    segmentTotalDistance = segmentDistance,
                )
                return JourneyProgress(
                    currentSegmentIndex = i,
                    progressInSegment = fraction.coerceIn(0f, 1f),
                    currentSpeed = currentSpeed,
                    distanceTraveled = accumulatedDistance + distanceInSegment,
                    totalDistance = totalDistance,
                    overallProgress = timeRatio,
                    nextStation = path.path.getOrNull(i + 1),
                    completedStations = path.path.take(i + 1),
                    isApproachingStation = isApproaching,
                    currentSegmentStartStation = path.path.getOrNull(i),
                    currentSegmentEndStation = path.path.getOrNull(i + 1),
                    distanceInCurrentSegment = distanceInSegment,
                    currentSegmentTotalDistance = segmentDistance,
                )
            }
            remaining -= segmentTime
            accumulatedDistance += segmentDistance

            // 到达站点 i+1；除终点外需停靠一段时间
            if (i + 1 < segmentCount) {
                val dwell = dwellSeconds(path.path[i + 1].id)
                if (remaining < dwell) {
                    return JourneyProgress(
                        currentSegmentIndex = i + 1,
                        progressInSegment = 0f,
                        currentSpeed = 0f,
                        distanceTraveled = accumulatedDistance,
                        totalDistance = totalDistance,
                        overallProgress = timeRatio,
                        nextStation = path.path.getOrNull(i + 2),
                        completedStations = path.path.take(i + 2),
                        isApproachingStation = false,
                        currentSegmentStartStation = path.path.getOrNull(i + 1),
                        currentSegmentEndStation = path.path.getOrNull(i + 2),
                        distanceInCurrentSegment = 0f,
                        currentSegmentTotalDistance = getSegmentDistance(path, i + 1),
                        isDwelling = true,
                    )
                }
                remaining -= dwell
            }
        }

        // 超出时间轴（理论上等于 totalSeconds，含舍入误差），视为已到达终点
        return createCompletedProgress(path)
    }

    /** 单个车站的停靠时长（秒） */
    private fun dwellSeconds(stationId: String): Int =
        TrainSpeedModel.dwellMinutesFor(stationId) * 60

    /**
     * 计算每段行驶时间（秒）。
     *
     * 优先使用边数据中的真实运行时间；旧数据缺少边时长时，
     * 把扣除停靠时间后的总时长按各段距离比例分配。
     */
    private fun resolveSegmentTimes(path: PathResult, totalSeconds: Int): IntArray {
        val segmentCount = path.path.size - 1
        val times = IntArray(segmentCount)

        var totalDwell = 0
        for (i in 1 until path.path.size - 1) {
            totalDwell += dwellSeconds(path.path[i].id)
        }

        var knownTotal = 0
        for (i in 0 until segmentCount) {
            val minutes = path.edges.getOrNull(i)?.durationMin ?: 0
            if (minutes > 0) {
                times[i] = minutes * 60
                knownTotal += times[i]
            }
        }

        val fallbackTotal = (totalSeconds - totalDwell).coerceAtLeast(1)
        if (knownTotal >= fallbackTotal) return times

        // 将剩余时间按距离比例分配给缺少时长的段
        val totalDistance = path.totalDistanceKm.toFloat()
        for (i in 0 until segmentCount) {
            if (times[i] == 0) {
                val share = if (totalDistance > 0f) {
                    getSegmentDistance(path, i) / totalDistance
                } else {
                    1f / segmentCount
                }
                times[i] = ((fallbackTotal - knownTotal) * share).toInt().coerceAtLeast(1)
            }
        }
        return times
    }
    
    /**
     * 获取指定段的距离
     *
     * @param path 路径
     * @param segmentIndex 段索引
     * @return 段距离 (km)
     */
    private fun getSegmentDistance(path: PathResult, segmentIndex: Int): Float {
        if (segmentIndex < 0 || segmentIndex >= (path.path.size - 1)) {
            return 0f
        }

        // 优先使用边的真实距离；旧数据（Gson 反序列化缺失字段）回退到平均距离估算
        val edgeDistance = path.edges.getOrNull(segmentIndex)?.distanceKm?.toFloat() ?: 0f
        if (edgeDistance > 0f) return edgeDistance

        return path.totalDistanceKm.toFloat() / (path.path.size - 1)
    }
    
    /**
     * 创建初始进度（旅程刚开始）
     */
    private fun createInitialProgress(path: PathResult): JourneyProgress {
        return JourneyProgress(
            currentSegmentIndex = 0,
            progressInSegment = 0f,
            currentSpeed = 0f,
            distanceTraveled = 0f,
            totalDistance = path.totalDistanceKm.toFloat(),
            overallProgress = 0f,
            nextStation = path.path.getOrNull(1),
            completedStations = emptyList(),
            isApproachingStation = false,
            currentSegmentStartStation = path.path.firstOrNull(),
            currentSegmentEndStation = path.path.getOrNull(1),
            distanceInCurrentSegment = 0f,
            currentSegmentTotalDistance = getSegmentDistance(path, 0),
        )
    }
    
    /**
     * 创建完成进度（旅程已结束）
     */
    private fun createCompletedProgress(path: PathResult): JourneyProgress {
        val lastSegmentIndex = (path.path.size - 2).coerceAtLeast(0)
        val lastSegmentDistance = getSegmentDistance(path, lastSegmentIndex)
        
        return JourneyProgress(
            currentSegmentIndex = lastSegmentIndex,
            progressInSegment = 1f,
            currentSpeed = 0f,
            distanceTraveled = path.totalDistanceKm.toFloat(),
            totalDistance = path.totalDistanceKm.toFloat(),
            overallProgress = 1f,
            nextStation = null,
            completedStations = path.path,
            isApproachingStation = false,
            currentSegmentStartStation = path.path.getOrNull(lastSegmentIndex),
            currentSegmentEndStation = path.path.lastOrNull(),
            distanceInCurrentSegment = lastSegmentDistance,
            currentSegmentTotalDistance = lastSegmentDistance,
        )
    }
    
    /**
     * 创建空进度（路径无效）
     */
    private fun createEmptyProgress(): JourneyProgress {
        return JourneyProgress(
            currentSegmentIndex = 0,
            progressInSegment = 0f,
            currentSpeed = 0f,
            distanceTraveled = 0f,
            totalDistance = 0f,
            overallProgress = 0f,
            nextStation = null,
            completedStations = emptyList(),
            isApproachingStation = false,
            currentSegmentStartStation = null,
            currentSegmentEndStation = null,
            distanceInCurrentSegment = 0f,
            currentSegmentTotalDistance = 0f,
        )
    }
    
}
