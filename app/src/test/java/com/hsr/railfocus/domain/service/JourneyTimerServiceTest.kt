package com.hsr.railfocus.domain.service

import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.model.journey.JourneyProgressTracker
import com.hsr.railfocus.domain.model.journey.StationArrivalDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JourneyTimerServiceTest {

    private lateinit var timerService: JourneyTimerService
    private var currentTime = 100_000L

    @Before
    fun setup() {
        currentTime = 100_000L
        val tracker = JourneyProgressTracker()
        val detector = StationArrivalDetector()
        timerService = JourneyTimerService(tracker, detector).apply {
            timeProvider = { currentTime }
        }
    }

    private fun createDummyPath(): PathResult {
        val s1 = Station(id = "1", name = "北京南", displayName = "北京南站", province = "北京", city = "北京", lat = 39.86, lng = 116.37)
        val s2 = Station(id = "2", name = "天津", displayName = "天津站", province = "天津", city = "天津", lat = 39.13, lng = 117.20)
        return PathResult(
            path = listOf(s1, s2),
            totalDurationMin = 10,
            totalDistanceKm = 120.0,
            edges = emptyList()
        )
    }

    @Test
    fun testStartWithProgress_initialState() = runTest {
        try {
            val path = createDummyPath()
            timerService.startWithProgress(path, durationSeconds = 600, scope = this)

            assertTrue(timerService.state.value is JourneyTimerService.TimerState.Running)
            val runningState = timerService.state.value as JourneyTimerService.TimerState.Running
            assertEquals(600, runningState.remainingSeconds)
            assertEquals(600, runningState.totalSeconds)
            assertEquals(path, timerService.getCurrentPath())
        } finally {
            timerService.stop()
        }
    }

    @Test
    fun testLockScreenWakeup_completesImmediatelyWhenElapsed() = runTest {
        try {
            val path = createDummyPath()
            timerService.startWithProgress(path, durationSeconds = 600, scope = this)

            // 模拟锁屏休眠：墙上时间/elapsedRealtime 过去了 601 秒，协程苏醒
            currentTime += 601_000L
            advanceTimeBy(1001)

            // 验证计时器立刻识别时间已满并进入 Completed 状态
            assertTrue(timerService.state.value is JourneyTimerService.TimerState.Completed)
            assertEquals(0, timerService.getRemainingSeconds())
        } finally {
            timerService.stop()
        }
    }

    @Test
    fun testPauseAndResume_accumulatesDelay() = runTest {
        try {
            val path = createDummyPath()
            timerService.startWithProgress(path, durationSeconds = 600, scope = this)

            // 暂停 60 秒
            timerService.pause()
            assertTrue(timerService.state.value is JourneyTimerService.TimerState.Paused)

            currentTime += 60_000L
            assertEquals(60, timerService.delaySeconds)
            assertEquals(1, timerService.delayMinutes)

            // 恢复运行
            timerService.resume(this)
            assertTrue(timerService.state.value is JourneyTimerService.TimerState.Running)

            // 经过 10 秒
            currentTime += 10_000L
            advanceTimeBy(1001)

            // 剩余时间应该是 600 - 10 = 590 秒（暂停时间不消耗旅程计时）
            val runningState = timerService.state.value as JourneyTimerService.TimerState.Running
            assertEquals(590, runningState.remainingSeconds)
        } finally {
            timerService.stop()
        }
    }

    @Test
    fun testBindScope_transfersExecutionToNewScope() = runTest {
        try {
            val path = createDummyPath()
            timerService.startWithProgress(path, durationSeconds = 300, scope = this)

            // 绑定到新的 scope（模拟前台服务的 serviceScope）
            timerService.bindScope(this)

            currentTime += 50_000L
            advanceTimeBy(1001)

            val running = timerService.state.value as JourneyTimerService.TimerState.Running
            assertEquals(250, running.remainingSeconds)
        } finally {
            timerService.stop()
        }
    }
}

