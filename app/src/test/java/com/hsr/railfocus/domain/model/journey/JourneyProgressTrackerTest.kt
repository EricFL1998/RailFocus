package com.hsr.railfocus.domain.model.journey

import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 旅程进度追踪器测试
 *
 * 验证时间轴语义：每段按边时长行驶，到达中间站后停靠随机 1-2 分钟，
 * 停靠期间速度为 0；以及 PathEdge 真实距离与旧数据平均距离回退两种路径。
 */
class JourneyProgressTrackerTest {

    private val stationA = station("a", "A站")
    private val stationB = station("b", "B站")
    private val stationC = station("c", "C站")

    /** 途经站 B 的停靠时长（分钟），按车站 ID 确定为 1 或 2 */
    private val dwellBMin = TrainSpeedModel.dwellMinutesFor("b")

    private lateinit var tracker: JourneyProgressTracker

    @Before
    fun setUp() {
        tracker = JourneyProgressTracker()
    }

    /** 旅程总时长（秒）= 两段行驶 20 分钟 + B 站停靠 */
    private fun totalSeconds() = (20 + dwellBMin) * 60

    private fun station(id: String, name: String) = Station(
        id = id,
        name = name,
        displayName = name,
        province = "",
        city = "",
        lat = 0.0,
        lng = 0.0,
        isMajor = false,
        tier = 3,
    )

    private fun edge(from: Station, to: Station, distanceKm: Double, durationMin: Int = 10) =
        PathResult.PathEdge(
            from = from,
            to = to,
            durationMin = durationMin,
            lineName = "",
            distanceKm = distanceKm,
        )

    /** A —100km/10min— B —50km/10min— C，含 B 站停靠，总时长与边数据一致 */
    private fun pathWithEdgeDistances() = PathResult(
        path = listOf(stationA, stationB, stationC),
        totalDurationMin = 20 + dwellBMin,
        totalDistanceKm = 150.0,
        edges = listOf(
            edge(stationA, stationB, 100.0),
            edge(stationB, stationC, 50.0),
        ),
    )

    @Test
    fun zeroElapsed_returnsInitialProgress() {
        val progress = tracker.calculateProgress(pathWithEdgeDistances(), 0, totalSeconds())

        assertEquals(0, progress.currentSegmentIndex)
        assertEquals(0f, progress.progressInSegment, 0.0001f)
        assertEquals(0f, progress.overallProgress, 0.0001f)
        assertEquals(stationB, progress.nextStation)
        assertTrue(progress.completedStations.isEmpty())
    }

    @Test
    fun fullElapsed_returnsCompletedProgress() {
        val progress = tracker.calculateProgress(pathWithEdgeDistances(), totalSeconds(), totalSeconds())

        assertEquals(1f, progress.overallProgress, 0.0001f)
        assertEquals(150f, progress.distanceTraveled, 0.0001f)
        assertNull(progress.nextStation)
        assertEquals(listOf(stationA, stationB, stationC), progress.completedStations)
    }

    @Test
    fun midSegment_usesEdgeDurationsForSegmentLocation() {
        // 第一段行驶 10 分钟，经过 5 分钟时位于第一段中点（50km 处）
        val progress = tracker.calculateProgress(pathWithEdgeDistances(), 300, totalSeconds())

        assertEquals(0, progress.currentSegmentIndex)
        assertEquals(0.5f, progress.progressInSegment, 0.0001f)
        assertEquals(50f, progress.distanceTraveled, 0.1f)
        assertEquals(stationB, progress.nextStation)
        assertEquals(listOf(stationA), progress.completedStations)
        assertEquals(stationA, progress.currentSegmentStartStation)
        assertEquals(stationB, progress.currentSegmentEndStation)
    }

    @Test
    fun duringDwell_trainIsStoppedAtStation() {
        // 到达 B（600 秒）后再过 30 秒，处于停靠期间
        val progress = tracker.calculateProgress(pathWithEdgeDistances(), 630, totalSeconds())

        assertTrue(progress.isDwelling)
        assertEquals(0f, progress.currentSpeed, 0.0001f)
        assertEquals(1, progress.currentSegmentIndex)
        assertEquals(0f, progress.progressInSegment, 0.0001f)
        assertEquals(stationB, progress.currentSegmentStartStation)
        assertEquals(stationC, progress.currentSegmentEndStation)
        assertEquals(listOf(stationA, stationB), progress.completedStations)
        assertEquals(100f, progress.distanceTraveled, 0.1f)
    }

    @Test
    fun afterDwell_startsSecondSegment() {
        // 过 B 站停靠后行驶 2 分钟 → 第二段 20% 处（110km 处）
        val elapsed = 600 + dwellBMin * 60 + 120
        val progress = tracker.calculateProgress(pathWithEdgeDistances(), elapsed, totalSeconds())

        assertFalse(progress.isDwelling)
        assertEquals(1, progress.currentSegmentIndex)
        assertEquals(0.2f, progress.progressInSegment, 0.01f)
        assertEquals(110f, progress.distanceTraveled, 0.1f)
        assertEquals(stationC, progress.nextStation)
        assertEquals(listOf(stationA, stationB), progress.completedStations)
    }

    @Test
    fun legacyPathWithoutEdgeDistances_fallsBackToDistanceProportionalTime() {
        // 旧数据：edges 没有时长，扣除停靠时间后按距离均分总时长
        val legacy = PathResult(
            path = listOf(stationA, stationB, stationC),
            totalDurationMin = 90,
            totalDistanceKm = 150.0,
            edges = emptyList(),
        )
        val legacyTotalSeconds = 90 * 60
        val segmentTime = (legacyTotalSeconds - dwellBMin * 60) / 2

        val progress = tracker.calculateProgress(legacy, segmentTime / 2, legacyTotalSeconds)

        // 每段均分一半时间，位于第一段中点（回退段距 75km 的 50% 处）
        assertEquals(0, progress.currentSegmentIndex)
        assertEquals(0.5f, progress.progressInSegment, 0.01f)
        assertEquals(37.5f, progress.distanceTraveled, 0.1f)
        assertEquals(stationB, progress.nextStation)
    }

    @Test
    fun invalidPath_returnsEmptyProgress() {
        val single = PathResult(
            path = listOf(stationA),
            totalDurationMin = 0,
            totalDistanceKm = 0.0,
            edges = emptyList(),
        )

        val progress = tracker.calculateProgress(single, 10, 60)

        assertEquals(0f, progress.totalDistance, 0.0001f)
        assertNull(progress.nextStation)
        assertTrue(progress.completedStations.isEmpty())
    }
}
