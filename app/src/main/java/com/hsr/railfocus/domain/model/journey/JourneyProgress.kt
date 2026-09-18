package com.hsr.railfocus.domain.model.journey

import com.hsr.railfocus.domain.model.Station

/**
 * 旅程进度状态
 * 
 * 包含火车在路径上的实时位置、速度等信息
 */
data class JourneyProgress(
    /**
     * 当前所在的段索引（0-based）
     * 例如：0 表示在第一段（站点0 → 站点1）
     */
    val currentSegmentIndex: Int,
    
    /**
     * 在当前段内的进度（0.0 ~ 1.0）
     * 0.0 = 刚离开起始站
     * 1.0 = 即将到达目标站
     */
    val progressInSegment: Float,
    
    /**
     * 当前瞬时速度 (km/h)
     */
    val currentSpeed: Float,
    
    /**
     * 已行驶的总距离 (km)
     */
    val distanceTraveled: Float,
    
    /**
     * 总旅程距离 (km)
     */
    val totalDistance: Float,
    
    /**
     * 总体进度（0.0 ~ 1.0）
     * 基于距离计算
     */
    val overallProgress: Float,
    
    /**
     * 下一个即将到达的站点
     */
    val nextStation: Station?,
    
    /**
     * 已经完成（经过）的站点列表
     */
    val completedStations: List<Station>,
    
    /**
     * 是否正在接近站点（距离 < 10km）
     * 用于触发减速动画和到站提示
     */
    val isApproachingStation: Boolean,
    
    /**
     * 当前段的起始站点
     */
    val currentSegmentStartStation: Station?,
    
    /**
     * 当前段的目标站点
     */
    val currentSegmentEndStation: Station?,
    
    /**
     * 当前段内已行驶的距离 (km)
     */
    val distanceInCurrentSegment: Float,
    
    /**
     * 当前段的总距离 (km)
     */
    val currentSegmentTotalDistance: Float,

    /**
     * 是否正在站点停靠（停站期间速度为 0）
     * 停靠在 currentSegmentStartStation，停靠结束后发车
     */
    val isDwelling: Boolean = false,
)
