package com.hsr.railfocus.domain.model.journey

import com.hsr.railfocus.domain.model.Station
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 站点到达事件检测器
 * 
 * 负责检测火车何时到达新站点，并触发相应事件
 * 防止重复触发同一站点的到达事件
 */
@Singleton
class StationArrivalDetector @Inject constructor() {
    
    /**
     * 已经到达过的站点ID集合
     * 用于防止重复触发
     */
    private val arrivedStationIds = mutableSetOf<String>()

    /**
     * 已经发送过"即将到达"事件的站点ID集合
     * 与 [arrivedStationIds] 分开，避免每次刷新都重复发送
     */
    private val notifiedUpcomingIds = mutableSetOf<String>()
    
    /**
     * 上一次检查的段索引
     */
    private var lastSegmentIndex = -1
    
    /**
     * 检测是否到达新站点
     * 
     * 检测逻辑：
     * 1. 当段索引发生变化时，说明经过了一个站点
     * 2. 确保该站点之前没有被标记为已到达
     * 
     * @param currentProgress 当前进度
     * @return 新到达的站点（如果有），否则返回 null
     */
    fun checkArrival(currentProgress: JourneyProgress): Station? {
        val currentSegmentIndex = currentProgress.currentSegmentIndex
        
        // 如果段索引没有变化，说明还在同一段，没有到达新站点
        if (currentSegmentIndex == lastSegmentIndex) {
            return null
        }
        
        // 段索引发生变化，说明可能到达了新站点
        // 到达的是当前段的起始站点（即上一段的结束站点）
        val arrivedStation = currentProgress.currentSegmentStartStation
        
        if ((arrivedStation != null && !arrivedStationIds.contains(arrivedStation.id))) {
            // 标记为已到达
            arrivedStationIds.add(arrivedStation.id)
            lastSegmentIndex = currentSegmentIndex
            return arrivedStation
        }
        
        // 更新段索引
        lastSegmentIndex = currentSegmentIndex
        return null
    }
    
    /**
     * 检测是否即将到达站点（用于提前触发动画）
     * 
     * @param currentProgress 当前进度
     * @return 即将到达的站点（如果有）
     */
    fun checkUpcomingArrival(
        currentProgress: JourneyProgress,
    ): Station? {
        // 如果正在接近站点（距离 < 10km）
        if (currentProgress.isApproachingStation) {
            val nextStation = currentProgress.nextStation

            // 确保该站点只发送一次"即将到达"事件
            if (nextStation != null &&
                !arrivedStationIds.contains(nextStation.id) &&
                notifiedUpcomingIds.add(nextStation.id)
            ) {
                return nextStation
            }
        }

        return null
    }
    
    /**
     * 重置检测状态
     * 
     * 在新旅程开始时调用，清除所有已到达站点的记录
     */
    fun reset() {
        arrivedStationIds.clear()
        notifiedUpcomingIds.clear()
        lastSegmentIndex = -1
    }
    
    /**
     * 手动标记站点为已到达
     * 
     * 用于初始化时标记起始站点
     */
    fun markAsArrived(stationId: String) {
        arrivedStationIds.add(stationId)
    }

    /**
     * 将检测器状态对齐到给定进度。
     *
     * 用于恢复旅程：已路过的站点不应再次触发到达/即将到达事件。
     */
    fun syncToProgress(progress: JourneyProgress) {
        lastSegmentIndex = progress.currentSegmentIndex
        progress.completedStations.forEach { arrivedStationIds.add(it.id) }
        progress.currentSegmentStartStation?.let { arrivedStationIds.add(it.id) }
        progress.nextStation?.let { notifiedUpcomingIds.add(it.id) }
    }
}
