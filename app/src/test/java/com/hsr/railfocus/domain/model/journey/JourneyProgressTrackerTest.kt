package com.hsr.railfocus.domain.model.journey

import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 旅程进度追踪器测试
 *
 * 验证时间→距离的映射、分段定位，以及
 * PathEdge 真实距离与旧数据平均距离回退两种路径。
 */
class JourneyProgressTrackerTest {

    private val stationA = station("a", "A站")
    private val stationB = station("b", "B站")
    private val stationC = station("c", "C站")

    private lateinit var tracker: JourneyProgressTracker

    @Before
    fun setUp() {
        tracker = JourneyProgressTracker()
    }

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

    /** A —100km— B —50km— C，总距离 150km */
    private fun pathWithEdgeDistances() = PathResult(
        path = listOf(stationA, stationB, stationC),
        totalDurationMin = 90,
        totalDistanceKm = 150.0,
        edges = listOf(
            edge(stationA, stationB, 100.0),
            edge(stationB, stationC, 50.0),
        ),
    )

    @Test
    fun zeroElapsed_returnsInitialProgress() {
        val progress = tracker.calculateProgress(pathWithEdgeDistances(), 0, 90)

        assertEquals(0, progress.currentSegmentIndex)
        assertEquals(0f, progress.progressInSegment, 0.0001f)
        assertEquals(0f, progress.overallProgress, 0.0001f)
        assertEquals(stationB, progress.nextStation)
        assertTrue(progress.completedStations.isEmpty())
    }

    @Test
    fun fullElapsed_returnsCompletedProgress() {
        val progress = tracker.calculateProgress(pathWithEdgeDistances(), 90, 90)

        assertEquals(1f, progress.overallProgress, 0.0001f)
        assertEquals(150f, progress.distanceTraveled, 0.0001f)
        assertNull(progress.nextStation)
        assertEquals(listOf(stationA, stationB, stationC), progress.completedStations)
    }

    @Test
    fun midJourney_usesEdgeDistancesForSegmentLocation() {
        // 时间过半 → 目标距离 75km，位于第一段（0-100km）的 75% 处
        val progress = tracker.calculateProgress(pathWithEdgeDistances(), 45, 90)

        assertEquals(0, progress.currentSegmentIndex)
        assertEquals(0.75f, progress.progressInSegment, 0.0001f)
        assertEquals(75f, progress.distanceTraveled, 0.1f)
        assertEquals(stationB, progress.nextStation)
        assertEquals(listOf(stationA), progress.completedStations)
        assertEquals(stationA, progress.currentSegmentStartStation)
        assertEquals(stationB, progress.currentSegmentEndStation)
    }

    @Test
    fun secondSegment_usesEdgeDistances() {
        // 目标距离 120km → 第二段（100-150km）的 40% 处
        val progress = tracker.calculateProgress(pathWithEdgeDistances(), 72, 90)

        assertEquals(1, progress.currentSegmentIndex)
        assertEquals(0.4f, progress.progressInSegment, 0.01f)
        assertEquals(stationC, progress.nextStation)
        assertEquals(listOf(stationA, stationB), progress.completedStations)
    }

    @Test
    fun legacyPathWithoutEdgeDistances_fallsBackToAverage() {
        // 旧数据：edges 没有距离，回退为平均段距（150/2 = 75km 每段）
        val legacy = PathResult(
            path = listOf(stationA, stationB, stationC),
            totalDurationMin = 90,
            totalDistanceKm = 150.0,
            edges = emptyList(),
        )

        val progress = tracker.calculateProgress(legacy, 45, 90)

        // 平均段距 75km，目标距离 75km 恰好落在第一段末尾
        assertEquals(0, progress.currentSegmentIndex)
        assertEquals(1f, progress.progressInSegment, 0.0001f)
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
