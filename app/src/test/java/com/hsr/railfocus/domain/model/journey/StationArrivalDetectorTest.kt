package com.hsr.railfocus.domain.model.journey

import com.hsr.railfocus.domain.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 站点到达检测器测试
 *
 * 重点验证两类事件都不会重复触发：
 * - 实际到达（段索引推进）
 * - 即将到达（接近站点阈值）
 */
class StationArrivalDetectorTest {

    private val stationA = station("a", "A站")
    private val stationB = station("b", "B站")
    private val stationC = station("c", "C站")

    private lateinit var detector: StationArrivalDetector

    @Before
    fun setUp() {
        detector = StationArrivalDetector()
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

    private fun progress(
        segmentIndex: Int,
        segmentStart: Station?,
        next: Station?,
        approaching: Boolean = false,
    ) = JourneyProgress(
        currentSegmentIndex = segmentIndex,
        progressInSegment = 0f,
        currentSpeed = 0f,
        distanceTraveled = 0f,
        totalDistance = 0f,
        overallProgress = 0f,
        nextStation = next,
        completedStations = emptyList(),
        isApproachingStation = approaching,
        currentSegmentStartStation = segmentStart,
        currentSegmentEndStation = next,
        distanceInCurrentSegment = 0f,
        currentSegmentTotalDistance = 0f,
    )

    @Test
    fun arrival_notFiredWhileSegmentUnchanged() {
        detector.markAsArrived(stationA.id)
        val p = progress(segmentIndex = 0, segmentStart = stationA, next = stationB)
        assertNull(detector.checkArrival(p))
    }

    @Test
    fun arrival_firesOnceWhenSegmentAdvances() {
        detector.markAsArrived(stationA.id)

        val first = detector.checkArrival(progress(1, stationB, stationC))
        assertEquals(stationB, first)

        // 同一站不重复触发
        assertNull(detector.checkArrival(progress(1, stationB, stationC)))

        // 推进到下一段触发下一站
        val second = detector.checkArrival(progress(2, stationC, null))
        assertEquals(stationC, second)
    }

    @Test
    fun arrival_skipsAlreadyArrivedStation() {
        detector.markAsArrived(stationA.id)
        detector.markAsArrived(stationB.id)

        // B 已标记为到达，即使段索引推进也不应再次触发
        assertNull(detector.checkArrival(progress(1, stationB, stationC)))
    }

    @Test
    fun upcoming_firesOnlyOncePerStation() {
        detector.markAsArrived(stationA.id)

        val p = progress(0, stationA, stationB, approaching = true)
        assertEquals(stationB, detector.checkUpcomingArrival(p))
        // 后续刷新不再重复发送
        assertNull(detector.checkUpcomingArrival(p))
        assertNull(detector.checkUpcomingArrival(p))
    }

    @Test
    fun upcoming_notFiredWhenNotApproaching() {
        val p = progress(0, stationA, stationB, approaching = false)
        assertNull(detector.checkUpcomingArrival(p))
    }

    @Test
    fun upcoming_notFiredForArrivedStation() {
        detector.markAsArrived(stationB.id)
        val p = progress(0, stationA, stationB, approaching = true)
        assertNull(detector.checkUpcomingArrival(p))
    }

    @Test
    fun reset_clearsAllState() {
        detector.markAsArrived(stationA.id)
        detector.checkArrival(progress(1, stationB, stationC))
        detector.checkUpcomingArrival(progress(1, stationB, stationC, approaching = true))

        detector.reset()

        // 重置后 B 可再次触发到达和即将到达
        assertEquals(stationB, detector.checkArrival(progress(1, stationB, stationC)))
        assertEquals(
            stationC,
            detector.checkUpcomingArrival(progress(1, stationB, stationC, approaching = true)),
        )
    }
}
