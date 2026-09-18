package com.hsr.railfocus.domain.model.journey

import com.hsr.railfocus.domain.model.PathResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 旅程进度追踪器
 * 
 * 负责根据时间计算火车在路径上的精确位置
 * 结合真实的高铁速度模型（加速、巡航、减速）
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
        
        // 计算总距离
        val totalDistance = path.totalDistanceKm.toFloat()

        // 计算时间比例
        val timeRatio = elapsedSeconds.toFloat() / totalSeconds.toFloat()

        // 将时间映射到距离（考虑加减速）
        val targetDistance = timeRatio * totalDistance

        // 找到当前所在的段和段内位置
        val segmentInfo = findCurrentSegment(path, targetDistance)

        // 计算当前速度
        val currentSpeed = speedModel.calculateSpeed(
            distanceInSegment = segmentInfo.distanceInSegment,
            segmentTotalDistance = segmentInfo.segmentDistance,
            elapsedSeconds = elapsedSeconds,
        )
        
        // 判断是否接近站点
        val isApproaching = speedModel.isApproachingStation(
            distanceInSegment = segmentInfo.distanceInSegment,
            segmentTotalDistance = segmentInfo.segmentDistance,
        )
        
        // 构建进度对象
        return JourneyProgress(
            currentSegmentIndex = segmentInfo.segmentIndex,
            progressInSegment = segmentInfo.progressInSegment,
            currentSpeed = currentSpeed,
            distanceTraveled = targetDistance,
            totalDistance = totalDistance,
            overallProgress = timeRatio,
            nextStation = path.path.getOrNull(segmentInfo.segmentIndex + 1),
            completedStations = path.path.take(segmentInfo.segmentIndex + 1),
            isApproachingStation = isApproaching,
            currentSegmentStartStation = path.path.getOrNull(segmentInfo.segmentIndex),
            currentSegmentEndStation = path.path.getOrNull(segmentInfo.segmentIndex + 1),
            distanceInCurrentSegment = segmentInfo.distanceInSegment,
            currentSegmentTotalDistance = segmentInfo.segmentDistance,
        )
    }
    
    /**
     * 根据目标距离找到当前所在的段
     */
    private fun findCurrentSegment(path: PathResult, targetDistance: Float): SegmentInfo {
        var accumulatedDistance = 0f
        
        // 遍历所有段
        for (i in 0 until (path.path.size - 1)) {
            val segmentDistance = getSegmentDistance(path, i)
            
            // 检查目标距离是否在当前段内
            if (targetDistance <= (accumulatedDistance + segmentDistance)) {
                val distanceInSegment = targetDistance - accumulatedDistance
                val progressInSegment = if (segmentDistance > 0) {
                    (distanceInSegment / segmentDistance).coerceIn(0f, 1f)
                } else {
                    0f
                }
                
                return SegmentInfo(
                    segmentIndex = i,
                    distanceInSegment = distanceInSegment,
                    segmentDistance = segmentDistance,
                    progressInSegment = progressInSegment,
                )
            }
            
            accumulatedDistance += segmentDistance
        }
        
        // 如果超出范围，返回最后一段
        val lastSegmentIndex = (path.path.size - 2).coerceAtLeast(0)
        val lastSegmentDistance = getSegmentDistance(path, lastSegmentIndex)
        
        return SegmentInfo(
            segmentIndex = lastSegmentIndex,
            distanceInSegment = lastSegmentDistance,
            segmentDistance = lastSegmentDistance,
            progressInSegment = 1f,
        )
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
    
    /**
     * 段信息（内部使用）
     */
    private data class SegmentInfo(
        val segmentIndex: Int,
        val distanceInSegment: Float,
        val segmentDistance: Float,
        val progressInSegment: Float,
    )
}
