package com.hsr.railfocus.domain.service

import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.model.journey.JourneyProgressTracker
import com.hsr.railfocus.domain.model.journey.StationArrivalDetector
import com.hsr.railfocus.domain.model.WeatherInfo
import com.hsr.railfocus.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    @Test
    fun testCityTransition_emittedDuringJourneyProgress() = runTest {
        val tracker = mockk<CityTransitionTracker>(relaxed = true)
        val s1 = Station(id = "1", name = "郑州东", province = "河南", city = "郑州", lat = 34.75, lng = 113.77)
        val s2 = Station(id = "2", name = "许昌东", province = "河南", city = "许昌", lat = 34.00, lng = 113.85)
        val path = PathResult(
            path = listOf(s1, s2),
            totalDurationMin = 20,
            totalDistanceKm = 80.0,
            edges = emptyList()
        )

        val weatherInfo = WeatherInfo("weathercn:101180406", "魏都", "许昌", 7, 22.0, 1000L)
        val transitionEvent = CityTransitionTracker.CityTransition("郑州", "许昌", weatherInfo, 34.1, 113.8)
        val weatherRepo = mockk<WeatherRepository>(relaxed = true)
        coEvery { weatherRepo.getWeather(any(), any(), any()) } returns weatherInfo

        // 模拟跨越边界瞬间触发一次切换，随后在同城市内不再重复触发
        coEvery { tracker.onLocationUpdate(any(), any()) } returns transitionEvent andThen null

        val serviceWithTracker = JourneyTimerService(
            progressTracker = JourneyProgressTracker(),
            arrivalDetector = StationArrivalDetector(),
            cityTransitionTracker = tracker,
            weatherRepository = weatherRepo,
        ).apply {
            timeProvider = { currentTime }
        }

        val receivedTransitions = mutableListOf<CityTransitionTracker.CityTransition>()
        val collectJob = launch {
            serviceWithTracker.cityTransition.collect {
                receivedTransitions.add(it)
            }
        }

        try {
            serviceWithTracker.startWithProgress(path, durationSeconds = 1200, scope = this)
            // 推进到接近终点前（最后 10km 减速入站阶段，触发 checkUpcomingArrival）
            currentTime += 1150_000L
            advanceTimeBy(1150_001)

            // 验证跨城事件成功被 SharedFlow 广播出来
            assertEquals(1, receivedTransitions.size)
            assertEquals("郑州", receivedTransitions[0].fromCity)
            assertEquals("许昌", receivedTransitions[0].toCity)
            assertEquals(7, receivedTransitions[0].weather.weatherCode)
        } finally {
            collectJob.cancel()
            serviceWithTracker.stop()
        }
    }
}
