package com.hsr.railfocus.domain.service

import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.model.WeatherCondition
import com.hsr.railfocus.domain.model.WeatherInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WeatherManagerTest {

    private val prefs: UserPreferencesRepository = mockk()
    private val weatherEnabledFlow = MutableStateFlow(true)
    private lateinit var weatherManager: WeatherManager

    private val homeRain = WeatherInfo(
        locationKey = "weathercn:101051306",
        areaName = "尖山",
        cityName = "双鸭山",
        weatherCode = 7, // 小雨
        temperatureC = 16.0,
        fetchedAt = 1000L,
    )

    private val journeySnow = WeatherInfo(
        locationKey = "weathercn:101050405",
        areaName = "桦南",
        cityName = "佳木斯",
        weatherCode = 14, // 小雪
        temperatureC = -2.0,
        fetchedAt = 2000L,
    )

    @Before
    fun setUp() {
        every { prefs.weatherDisplayEnabled } returns weatherEnabledFlow
        weatherManager = WeatherManager(prefs)
    }

    @Test
    fun homeWeather_drivesActiveConditionWhenNoJourney() = runTest {
        weatherManager.setHomeWeather(homeRain)
        assertEquals(homeRain, weatherManager.activeWeather.filterNotNull().first())
        assertEquals(WeatherCondition.LIGHT_RAIN, weatherManager.activeCondition.filterNotNull().first())
    }

    @Test
    fun journeyWeather_takesPrecedenceWhenJourneyIsActive() = runTest {
        weatherManager.setHomeWeather(homeRain)
        weatherManager.setJourneyActive(true)
        weatherManager.setJourneyWeather(journeySnow)

        // 旅程中跨入新城市，天气应切换为旅程实况天气（雪）
        assertEquals(journeySnow, weatherManager.activeWeather.first { it == journeySnow })
        assertEquals(WeatherCondition.LIGHT_SNOW, weatherManager.activeCondition.first { it == WeatherCondition.LIGHT_SNOW })
    }

    @Test
    fun journeyEnded_fallsBackToHomeWeather() = runTest {
        weatherManager.setHomeWeather(homeRain)
        weatherManager.setJourneyActive(true)
        weatherManager.setJourneyWeather(journeySnow)
        assertEquals(WeatherCondition.LIGHT_SNOW, weatherManager.activeCondition.first { it == WeatherCondition.LIGHT_SNOW })

        // 旅程结束
        weatherManager.setJourneyActive(false)
        assertEquals(WeatherCondition.LIGHT_RAIN, weatherManager.activeCondition.first { it == WeatherCondition.LIGHT_RAIN })
    }

    @Test
    fun switchDisabled_clearsAllActiveWeather() = runTest {
        weatherManager.setHomeWeather(homeRain)
        assertEquals(WeatherCondition.LIGHT_RAIN, weatherManager.activeCondition.filterNotNull().first())

        // 用户关闭设置中的“天气显示”开关
        weatherEnabledFlow.value = false
        assertEquals(null, weatherManager.activeWeather.first { it == null })
        assertEquals(null, weatherManager.activeCondition.first { it == null })
    }
}
