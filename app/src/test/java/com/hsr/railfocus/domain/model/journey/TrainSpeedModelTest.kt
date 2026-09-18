package com.hsr.railfocus.domain.model.journey

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 高铁速度模型单元测试
 * 
 * 验证加速、巡航、减速的物理特性
 */
class TrainSpeedModelTest {
    
    private lateinit var speedModel: TrainSpeedModel
    
    @Before
    fun setup() {
        speedModel = TrainSpeedModel()
    }
    
    @Test
    fun `加速阶段 - 起点速度为0`() {
        val speed = speedModel.calculateSpeed(
            distanceInSegment = 0f,
            segmentTotalDistance = 100f,
            elapsedSeconds = 0,
        )
        
        assertEquals(0f, speed, 0.1f)
    }
    
    @Test
    fun `加速阶段 - 速度逐渐增加`() {
        val speed1km = speedModel.calculateSpeed(
            distanceInSegment = 1f,
            segmentTotalDistance = 100f,
            elapsedSeconds = 10
        )
        
        val speed3km = speedModel.calculateSpeed(
            distanceInSegment = 3f,
            segmentTotalDistance = 100f,
            elapsedSeconds = 30
        )
        
        val speed5km = speedModel.calculateSpeed(
            distanceInSegment = 5f,
            segmentTotalDistance = 100f,
            elapsedSeconds = 50
        )
        
        // 速度应该递增
        assertTrue("1km处速度应该 < 3km处速度", speed1km < speed3km)
        assertTrue("3km处速度应该 < 5km处速度", speed3km < speed5km)

        // 加速阶段结束时应该接近最高速度
        assertTrue("7km处速度应该接近最高速度 (actual=$speed5km)", speed5km > 200f)
    }
    
    @Test
    fun `巡航阶段 - 速度接近最高速度`() {
        val speed = speedModel.calculateSpeed(
            distanceInSegment = 50f,  // 段中间
            segmentTotalDistance = 100f,
            elapsedSeconds = 100
        )
        
        // 巡航速度应该在 285-315 km/h 之间（300 ± 5%）
        assertTrue("巡航速度应该 >= 285 km/h", speed >= 285f)
        assertTrue("巡航速度应该 <= 315 km/h", speed <= 315f)
    }
    
    @Test
    fun `巡航阶段 - 速度有波动`() {
        val speeds = mutableListOf<Float>()
        
        // 采样 10 个不同时刻的速度
        for (i in 0..9) {
            val speed = speedModel.calculateSpeed(
                distanceInSegment = 50f,
                segmentTotalDistance = 100f,
                elapsedSeconds = i * 10
            )
            speeds.add(speed)
        }
        
        // 速度不应该完全相同（有波动）
        val uniqueSpeeds = speeds.distinct()
        assertTrue("巡航速度应该有波动", uniqueSpeeds.size > 1)
        
        // 所有速度都应该在合理范围内
        speeds.forEach { speed ->
            assertTrue("速度应该在 285-315 范围内", (speed in 285f..315f))
        }
    }
    
    @Test
    fun `减速阶段 - 终点速度为0`() {
        val speed = speedModel.calculateSpeed(
            distanceInSegment = 100f,  // 段末尾
            segmentTotalDistance = 100f,
            elapsedSeconds = 1000
        )
        
        assertEquals(0f, speed, 0.1f)
    }
    
    @Test
    fun `减速阶段 - 速度逐渐减小`() {
        val segmentDistance = 100f
        
        // 剩余 10km（刚进入减速阶段）
        val speed10km = speedModel.calculateSpeed(
            distanceInSegment = segmentDistance - 10f,
            segmentTotalDistance = segmentDistance,
            elapsedSeconds = 100
        )
        
        // 剩余 5km
        val speed5km = speedModel.calculateSpeed(
            distanceInSegment = segmentDistance - 5f,
            segmentTotalDistance = segmentDistance,
            elapsedSeconds = 200
        )
        
        // 剩余 1km
        val speed1km = speedModel.calculateSpeed(
            distanceInSegment = segmentDistance - 1f,
            segmentTotalDistance = segmentDistance,
            elapsedSeconds = 300
        )
        
        // 速度应该递减
        assertTrue("剩余10km速度应该 > 剩余5km速度", speed10km > speed5km)
        assertTrue("剩余5km速度应该 > 剩余1km速度", speed5km > speed1km)
        
        // 进入减速阶段时速度应该还比较高
        assertTrue("剩余10km时速度应该 > 250 km/h", speed10km > 250f)
    }
    
    @Test
    fun `接近站点检测 - 剩余10km内为true`() {
        assertTrue(
            speedModel.isApproachingStation(
                distanceInSegment = 91f,
                segmentTotalDistance = 100f
            )
        )
    }
    
    @Test
    fun `接近站点检测 - 剩余10km外为false`() {
        assertFalse(
            speedModel.isApproachingStation(
                distanceInSegment = 89f,
                segmentTotalDistance = 100f
            )
        )
    }
    
    @Test
    fun 速度百分比计算() {
        assertEquals(0f, speedModel.getSpeedPercentage(0f), 0.01f)
        assertEquals(0.5f, speedModel.getSpeedPercentage(150f), 0.01f)
        assertEquals(1f, speedModel.getSpeedPercentage(300f), 0.01f)
        assertEquals(1f, speedModel.getSpeedPercentage(400f), 0.01f) // 超出最高速度
    }
    
    @Test
    fun `速度格式化`() {
        assertEquals("0 km/h", speedModel.formatSpeed(0f))
        assertEquals("150 km/h", speedModel.formatSpeed(150.4f))
        assertEquals("300 km/h", speedModel.formatSpeed(300f))
    }
    
    @Test
    fun `完整旅程 - 速度曲线连续性`() {
        val segmentDistance = 100f
        val speeds = mutableListOf<Float>()
        
        // 每隔 5km 采样一次速度
        for (distance in 0..100 step 5) {
            val speed = speedModel.calculateSpeed(
                distanceInSegment = distance.toFloat(),
                segmentTotalDistance = segmentDistance,
                elapsedSeconds = distance * 10
            )
            speeds.add(speed)
        }
        
        // 验证速度曲线的连续性（相邻点速度差不会太大）
        for (i in 0 until speeds.size - 1) {
            val speedDiff = kotlin.math.abs(speeds[i + 1] - speeds[i])
            assertTrue(
                "相邻5km速度差应该 < 300 km/h (实际: $speedDiff)",
                speedDiff < 300f
            )
        }
    }
    
    @Test
    fun `旅行时间 - 短距离应在合理范围内`() {
        // 7 km 短距离：不应因单位错误而耗时 60+ 分钟
        val time7km = speedModel.computeTravelTimeSeconds(7f)
        assertTrue("7km 旅行时间应 < 15 分钟 (实际: ${time7km}s)", time7km < 15 * 60)
        assertTrue("7km 旅行时间应 > 0 (实际: ${time7km}s)", time7km > 0)
    }
    
    @Test
    fun `旅行时间 - 中距离应在合理范围内`() {
        // 南京南 -> 镇江 ≈ 66.9 km，应在 ~20-35 分钟量级
        val time67km = speedModel.computeTravelTimeSeconds(66.9f)
        assertTrue("67km 旅行时间应 < 60 分钟 (实际: ${time67km}s)", time67km < 60 * 60)
        assertTrue("67km 旅行时间应 > 10 分钟 (实际: ${time67km}s)", time67km > 10 * 60)
    }
    
    @Test
    fun `旅行时间 - 长距离应在合理范围内`() {
        // 200 km 应在 1 小时左右
        val time200km = speedModel.computeTravelTimeSeconds(200f)
        assertTrue("200km 旅行时间应 < 90 分钟 (实际: ${time200km}s)", time200km < 90 * 60)
        assertTrue("200km 旅行时间应 > 30 分钟 (实际: ${time200km}s)", time200km > 30 * 60)
    }

    @Test
    fun `真实运行时间 - 长距离慢于平均速度估算`() {
        // 300 km 按 300 km/h 平均只需 60 分钟，但加速+减速会让实际更长。
        val minutes = speedModel.computeTravelTimeMinutes(300f)
        assertTrue("300km 应超过 60 分钟（实际: $minutes）", minutes > 60)
    }

    @Test
    fun `真实运行时间 - 短距离仍有合理正值`() {
        // 2 km 虽然短，但列车需要加速、减速，仍应给出合理时间。
        val minutes = speedModel.computeTravelTimeMinutes(2f)
        assertTrue("2km 至少 1 分钟（实际: $minutes）", minutes >= 1)
    }
}
