package com.hsr.railfocus.domain.model.journey

import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 高铁速度模型
 * 
 * 模拟真实的高铁运行特性：
 * - 出站加速（0 → 最高速度）
 * - 中途巡航（恒定高速，带小幅波动）
 * - 到站减速（最高速度 → 0）
 * 
 * 物理参数参考：
 * - 中国高铁加速度：约 0.4-0.6 m/s²
 * - 最高运营速度：300-350 km/h
 * - 加速距离：约 5-8 km
 * - 减速距离：约 8-12 km
 */
class TrainSpeedModel {
    
    companion object {
        // ===== 速度参数 =====
        /** 最高巡航速度 (km/h) */
        const val MAX_SPEED = 300f

        /** 加速度 (m/s²) */
        const val ACCELERATION = 0.5f
        
        /** 减速度 (m/s²) - 通常略大于加速度 */
        const val DECELERATION = 0.6f
        
        /** 巡航速度波动范围（百分比） */
        const val CRUISE_SPEED_VARIATION = 0.05f  // ±5%
        
        // ===== 距离参数 =====
        /** 加速阶段距离 (km) */
        const val ACCELERATION_DISTANCE = 7f
        
        /** 减速阶段距离 (km) */
        const val DECELERATION_DISTANCE = 10f
        
        /** 接近站点的阈值距离 (km) - 用于触发到站动画 */
        const val APPROACHING_STATION_THRESHOLD = 10f
        
        // ===== 波动参数 =====
        /** 速度波动周期（秒）- 控制波动频率 */
        const val VARIATION_PERIOD = 30f
        
        /** 速度波动振幅系数 - 多个正弦波叠加 */
        private val VARIATION_HARMONICS = listOf(
            1f to 1.0f,      // 基频
            0.3f to 2.3f,    // 第二谐波
            0.15f to 4.7f,    // 第三谐波
        )
    }
    
    /**
     * 计算给定位置的瞬时速度
     * 
     * @param distanceInSegment 当前段内已行驶的距离 (km)
     * @param segmentTotalDistance 当前段的总距离 (km)
     * @param elapsedSeconds 旅程已经过的总时间（秒）- 用于速度波动
     * @return 当前速度 (km/h)
     */
    fun calculateSpeed(
        distanceInSegment: Float,
        segmentTotalDistance: Float,
        elapsedSeconds: Int,
    ): Float {
        // 1. 鲁棒性检查：如果计时已开始，根据时间计算起步速度，避免 UI 显示 0
        // 防止因距离计算精度、NaN 或极小值导致速度卡在 0
        if (elapsedSeconds > 0 && (distanceInSegment <= 0.0001f || distanceInSegment.isNaN())) {
            val speedFromTime = (ACCELERATION * elapsedSeconds) * 3.6f // m/s -> km/h
            return min(speedFromTime, 20f) // 起步补偿上限设为 20km/h
        }

        // 2. 边界检查
        if (segmentTotalDistance <= 0f || segmentTotalDistance.isNaN()) return 0f
        if (distanceInSegment <= 0f || distanceInSegment.isNaN()) return 0f
        if (distanceInSegment >= segmentTotalDistance) return 0f
        
        // 3. 计算距离段末尾的剩余距离
        val remainingDistance = segmentTotalDistance - distanceInSegment
        if (remainingDistance <= 0f || remainingDistance.isNaN()) return 0f
        
        // 4. 判断当前处于哪个阶段
        return when {
            // A. 特殊情况：短距离段，加速和减速阶段重叠
            distanceInSegment < ACCELERATION_DISTANCE && remainingDistance < DECELERATION_DISTANCE -> {
                min(calculateAccelerationSpeed(distanceInSegment), calculateDecelerationSpeed(remainingDistance))
            }

            // B. 加速阶段（段开始的前 ACCELERATION_DISTANCE km）
            distanceInSegment < ACCELERATION_DISTANCE -> {
                calculateAccelerationSpeed(distanceInSegment)
            }
            
            // C. 减速阶段（段末尾的最后 DECELERATION_DISTANCE km）
            remainingDistance < DECELERATION_DISTANCE -> {
                calculateDecelerationSpeed(remainingDistance)
            }
            
            // D. 巡航阶段（中间部分）
            else -> {
                calculateCruiseSpeed(elapsedSeconds)
            }
        }
    }
    
    /**
     * 计算加速阶段的速度
     * 
     * 使用物理公式：v² = 2as
     * 其中：v = 速度, a = 加速度, s = 距离
     * 
     * 速度曲线为抛物线型，符合真实物理
     */
    private fun calculateAccelerationSpeed(distance: Float): Float {
        // 将加速度从 m/s² 转换为 km/h² 的等效值：
        // 1 m/s² = 12960 km/h² = 3600² / 1000
        val accelerationKmH = ACCELERATION * 12960f
        
        // v² = 2as，所以 v = sqrt(2as)
        val speedSquared = 2f * accelerationKmH * distance
        val speed = sqrt(speedSquared)
        
        // 限制最高速度
        return min(speed, MAX_SPEED)
    }
    
    /**
     * 计算减速阶段的速度
     * 
     * 使用反向的物理公式：v² = 2as
     * 速度从最高速度逐渐降至 0
     */
    private fun calculateDecelerationSpeed(remainingDistance: Float): Float {
        // 将减速度从 m/s² 转换为 km/h² 的等效值：
        // 1 m/s² = 12960 km/h² = 3600² / 1000
        val decelerationKmH = DECELERATION * 12960f
        
        // v² = 2as，基于剩余距离计算速度
        val speedSquared = 2f * decelerationKmH * remainingDistance
        val speed = sqrt(speedSquared)
        
        // 限制最高速度
        return min(speed, MAX_SPEED)
    }
    
    /**
     * 计算巡航阶段的速度（带真实感的波动）
     * 
     * 使用多个正弦波叠加，模拟：
     * - 轨道微小起伏
     * - 风阻变化
     * - 牵引力调整
     * - 弯道限速
     */
    private fun calculateCruiseSpeed(elapsedSeconds: Int): Float {
        val variation = generateSpeedVariation(elapsedSeconds)
        
        // 基础速度 + 波动
        return MAX_SPEED * (1f + variation)
    }
    
    /**
     * 生成速度波动（多谐波正弦叠加）
     * 
     * 使用多个不同频率的正弦波叠加，创造自然的波动效果
     * 
     * @param timeSeconds 时间（秒）
     * @return 波动值（-CRUISE_SPEED_VARIATION ~ +CRUISE_SPEED_VARIATION）
     */
    private fun generateSpeedVariation(timeSeconds: Int): Float {
        var totalVariation = 0f
        
        // 叠加多个谐波
        for ((amplitude, frequency) in VARIATION_HARMONICS) {
            val phase = (2f * Math.PI.toFloat() * frequency * timeSeconds / VARIATION_PERIOD)
            totalVariation += amplitude * sin(phase)
        }
        
        // 归一化并应用波动范围
        val normalizedVariation = totalVariation / VARIATION_HARMONICS.sumOf { it.first.toDouble() }.toFloat()
        return normalizedVariation * CRUISE_SPEED_VARIATION
    }
    
    /**
     * 判断是否正在接近站点（用于触发 UI 动画）
     * 
     * @param distanceInSegment 当前段内已行驶的距离
     * @param segmentTotalDistance 当前段的总距离
     * @return true 如果即将到达站点
     */
    fun isApproachingStation(
        distanceInSegment: Float,
        segmentTotalDistance: Float,
    ): Boolean {
        val remainingDistance = segmentTotalDistance - distanceInSegment
        return remainingDistance < APPROACHING_STATION_THRESHOLD
    }
    
    /**
     * 获取速度的百分比表示（用于 UI 显示）
     * 
     * @param speed 当前速度 (km/h)
     * @return 速度百分比 (0.0 ~ 1.0)
     */
    fun getSpeedPercentage(speed: Float): Float {
        return (speed / MAX_SPEED).coerceIn(0f, 1f)
    }
    
    /**
     * 格式化速度显示
     * 
     * @param speed 速度 (km/h)
     * @return 格式化的字符串，如 "285 km/h"
     */
    fun formatSpeed(speed: Float): String {
        return "${speed.toInt()} km/h"
    }

    /**
     * 根据距离计算旅行时间（秒）
     *
     * 使用物理模型（加速→巡航→减速）通过数值积分计算：
     * - 加速阶段：v(s) = sqrt(2 * a * s)
     * - 巡航阶段：v(s) = MAX_SPEED
     * - 减速阶段：v(s) = sqrt(2 * dec * remaining)
     *
     * 不考虑速度波动（正弦叠加），因为波动平均值 ≈ 0，
     * 且计算时间时不需要随机性。
     *
     * @param distanceKm 段距离 (km)
     * @return 旅行时间（秒），至少为 1 秒
     */
    fun computeTravelTimeSeconds(distanceKm: Float): Int {
        if (distanceKm <= 0f) return 0

        // 短距离：加速和减速阶段重叠，永远达不到 MAX_SPEED
        // 峰值速度由 v²/(2a) + v²/(2dec) = d 推导得出
        val accDecOverlap = distanceKm < (ACCELERATION_DISTANCE + DECELERATION_DISTANCE)

        val steps = 200
        val ds = distanceKm / steps
        var totalTime = 0f

        for (i in 0 until steps) {
            val s = (i + 0.5f) * ds  // 中点法
            val remaining = distanceKm - s

            // 根据当前位置计算瞬时速度（不含波动）
            val speed = when {
                // 短距离重叠情况：用加速度和减速度的合成效果
                accDecOverlap && remaining < DECELERATION_DISTANCE -> {
                    // 从加速侧和减速侧分别计算
                    val accSpeed = calculateAccelerationSpeed(s)
                    val decSpeed = calculateDecelerationSpeed(remaining)
                    min(accSpeed, decSpeed)
                }
                s < ACCELERATION_DISTANCE -> calculateAccelerationSpeed(s)
                remaining < DECELERATION_DISTANCE -> calculateDecelerationSpeed(remaining)
                else -> MAX_SPEED
            }

            // dt = ds / v (小时) → 转换为秒
            if (speed > 0f) {
                totalTime += ds / speed * 3600f
            }
        }

        return totalTime.toInt().coerceAtLeast(1)
    }

    /**
     * 根据距离计算旅行时间（分钟）
     * 便于与图搜索中的 durationMin 对接
     *
     * @param distanceKm 段距离 (km)
     * @return 旅行时间（分钟），至少为 1 分钟
     */
    fun computeTravelTimeMinutes(distanceKm: Float): Int {
        return (computeTravelTimeSeconds(distanceKm) / 60f).toInt().coerceAtLeast(1)
    }
}
