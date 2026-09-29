package com.hsr.railfocus.domain.service

import com.hsr.railfocus.data.repository.WeatherRepository
import com.hsr.railfocus.domain.model.WeatherInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "列车开到哪里算进入另一个城市"的判定测试：
 * 跨越 0.1° 地理网格后第一次成功解析归属地时判定，
 * 以地级市名（geo affiliation 第一段）变化为准。
 */
class CityTransitionTrackerTest {

    private val weatherRepository: WeatherRepository = mockk()
    private val tracker = CityTransitionTracker(weatherRepository)

    private fun weather(city: String, area: String = "某区") = WeatherInfo(
        locationKey = "weathercn:101180101",
        areaName = area,
        cityName = city,
        weatherCode = 7,
        temperatureC = 21.0,
        fetchedAt = 1000L,
    )

    @Test
    fun sameGrid_updatesAreIgnoredWithoutIO() = runTest {
        coEvery { weatherRepository.getWeather(34.74, 113.62) } returns weather("郑州", "中原")

        // 首次解析：建立当前城市
        assertNull(tracker.onLocationUpdate(34.74, 113.62))
        assertEquals("郑州", tracker.currentCity)

        // 同一网格内继续上报（高铁约 2 分钟才穿过一个网格），不应再查仓库
        assertNull(tracker.onLocationUpdate(34.71, 113.61))
        assertNull(tracker.onLocationUpdate(34.70, 113.60))
        coVerify(exactly = 1) { weatherRepository.getWeather(any(), any()) }
    }

    @Test
    fun crossingGridWithinSameCity_doesNotTriggerTransition() = runTest {
        coEvery { weatherRepository.getWeather(34.74, 113.62) } returns weather("郑州", "中原")
        // 跨网格到荥阳（weathercn:101180103），区县级变了但仍是郑州市
        coEvery { weatherRepository.getWeather(34.78, 113.54) } returns weather("郑州", "荥阳")

        assertNull(tracker.onLocationUpdate(34.74, 113.62))
        val transition = tracker.onLocationUpdate(34.78, 113.54)

        // 市内跨县：不算进入另一个城市，但天气应刷新为新区县的数据
        assertNull(transition)
        assertEquals("荥阳", tracker.currentWeather?.areaName)
    }

    @Test
    fun enteringAnotherCity_triggersTransition() = runTest {
        coEvery { weatherRepository.getWeather(34.74, 113.62) } returns weather("郑州", "中原")
        coEvery { weatherRepository.getWeather(34.15, 113.82) } returns weather("许昌", "魏都")

        assertNull(tracker.onLocationUpdate(34.74, 113.62))
        val transition = tracker.onLocationUpdate(34.15, 113.82)

        assertNotNull(transition)
        assertEquals("郑州", transition!!.fromCity)
        assertEquals("许昌", transition.toCity)
        assertEquals(7, transition.weather.weatherCode)
        assertEquals("许昌", tracker.currentCity)
        assertEquals("许昌", tracker.currentWeather?.cityName)
    }

    @Test
    fun networkFailure_keepsStickyStateAndRetriesNextPoint() = runTest {
        coEvery { weatherRepository.getWeather(34.74, 113.62) } returns weather("郑州", "中原")
        assertNull(tracker.onLocationUpdate(34.74, 113.62))

        // 进入新网格但网络失败（高铁穿隧道）：保持旧城市，不更新网格
        coEvery { weatherRepository.getWeather(34.15, 113.82) } returns null
        assertNull(tracker.onLocationUpdate(34.15, 113.82))
        assertEquals("郑州", tracker.currentCity)

        // 网络恢复后，同一位置重试并成功判定跨城
        coEvery { weatherRepository.getWeather(34.15, 113.82) } returns weather("许昌", "魏都")
        val transition = tracker.onLocationUpdate(34.15, 113.82)
        assertNotNull(transition)
        assertEquals("许昌", transition!!.toCity)
    }

    @Test
    fun blankCityName_neverTriggersTransition() = runTest {
        // geo 未成功（cityName 为空）但天气可用
        coEvery { weatherRepository.getWeather(any(), any()) } returns weather("")

        assertNull(tracker.onLocationUpdate(34.74, 113.62))
        assertNull(tracker.onLocationUpdate(34.15, 113.82))
        assertNull(tracker.currentCity)
        // 天气仍然记录，供后续展示
        assertNotNull(tracker.currentWeather)
    }

    @Test
    fun reset_clearsJourneyState() = runTest {
        coEvery { weatherRepository.getWeather(34.74, 113.62) } returns weather("郑州", "中原")
        tracker.onLocationUpdate(34.74, 113.62)
        assertEquals("郑州", tracker.currentCity)

        tracker.reset()
        assertNull(tracker.currentCity)
        assertNull(tracker.currentWeather)

        // 重置后同一点位会重新解析
        tracker.onLocationUpdate(34.74, 113.62)
        coVerify(exactly = 2) { weatherRepository.getWeather(34.74, 113.62) }
    }

    @Test
    fun checkApproachingBoundary_withinSameGrid_doesNotQuery() = runTest {
        coEvery { weatherRepository.getWeather(any(), any()) } returns weather("双鸭山", "尖山")
        // 当前点与前瞻点在同一网格内（11km 内）
        val res = tracker.checkApproachingBoundary(46.68, 131.14, 46.69, 131.13)
        assertNull(res)
        coVerify(exactly = 0) { weatherRepository.getWeather(any(), any()) }
    }

    @Test
    fun checkApproachingBoundary_approachingNewCounty_prefetchesWeather() = runTest {
        // 当前点在双鸭山尖山区，前瞻点跨入双鸭山岭东区（县/区边界）
        coEvery { weatherRepository.getWeather(46.68, 131.14) } returns weather("双鸭山", "尖山")
        coEvery { weatherRepository.getWeather(46.58, 131.02) } returns weather("双鸭山", "岭东")

        val result = tracker.checkApproachingBoundary(
            currentLat = 46.68,
            currentLon = 131.14,
            aheadLat = 46.58,
            aheadLon = 131.02,
        )

        assertNotNull(result)
        assertEquals("岭东", result!!.areaName)
        assertEquals("双鸭山", result.cityName)

        // 重复探测同一前方网格不重复请求
        val second = tracker.checkApproachingBoundary(
            currentLat = 46.68,
            currentLon = 131.14,
            aheadLat = 46.58,
            aheadLon = 131.02,
        )
        assertNull(second)
    }

    @Test
    fun checkApproachingBoundary_approachingNewCity_prefetchesWeather() = runTest {
        // 当前在双鸭山岭东区，前瞻点 10km 进入佳木斯桦南县（城市边界）
        coEvery { weatherRepository.getWeather(46.58, 131.02) } returns weather("双鸭山", "岭东")
        coEvery { weatherRepository.getWeather(46.36, 130.68) } returns weather("佳木斯", "桦南")

        val result = tracker.checkApproachingBoundary(
            currentLat = 46.58,
            currentLon = 131.02,
            aheadLat = 46.36,
            aheadLon = 130.68,
        )

        assertNotNull(result)
        assertEquals("佳木斯", result!!.cityName)
        assertEquals("桦南", result.areaName)
    }
}
