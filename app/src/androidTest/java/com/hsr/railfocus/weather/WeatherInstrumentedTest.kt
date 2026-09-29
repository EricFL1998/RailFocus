package com.hsr.railfocus.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hsr.railfocus.data.local.UserDatabase
import com.hsr.railfocus.data.remote.weather.XiaomiWeatherApi
import com.hsr.railfocus.data.repository.WeatherRepository
import com.hsr.railfocus.domain.service.CityTransitionTracker
import com.hsr.railfocus.util.GeoGrid
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 天气数据层的真机/模拟器端到端测试：
 * 真实网络请求小米天气 API + 真实 UserDatabase（验证 v7 迁移后 weather_cache 可用）。
 *
 * 需要设备能访问 weatherapi.market.xiaomi.com。
 */
@RunWith(AndroidJUnit4::class)
class WeatherInstrumentedTest {

    private lateinit var api: XiaomiWeatherApi
    private lateinit var repository: WeatherRepository

    // 郑州东站坐标
    private val zzEast = 34.7594 to 113.7736

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = UserDatabase.getInstance(context)
        api = XiaomiWeatherApi()
        repository = WeatherRepository(api, db.weatherDataAccess())
        // 清理天气缓存（只动 weather_cache 表，不影响用户数据）
        runBlocking { db.weatherDataAccess().deleteAll() }
    }

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { UserDatabase.getInstance(context).weatherDataAccess().deleteAll() }
    }

    @Test(timeout = 30_000)
    fun reverseGeo_returnsCountyLevelArea() = runBlocking {
        val geo = api.reverseGeo(zzEast.first, zzEast.second)

        assertNotNull("geo 反查失败（检查网络）", geo)
        assertTrue(geo!!.locationKey.startsWith("weathercn:"))
        assertEquals("郑州", geo.cityName)
        assertTrue("区县名不应为空", geo.areaName.isNotBlank())
    }

    @Test(timeout = 30_000)
    fun fetchCurrent_returnsWeatherCodeAndTemperature() = runBlocking {
        val current = api.fetchCurrent(zzEast.first, zzEast.second, null)

        assertNotNull("天气查询失败（检查网络）", current)
        assertTrue("天气代码应在 0..58", current!!.weatherCode in 0..58)
        assertTrue("气温应在合理范围", current.temperatureC in -50.0..60.0)
    }

    @Test(timeout = 30_000)
    fun repository_fetchesPersistsAndServesFromCache() = runBlocking {
        val first = repository.getWeather(zzEast.first, zzEast.second)

        assertNotNull("首次获取失败", first)
        assertEquals("郑州", first!!.cityName)
        assertTrue(first.weatherCode in 0..58)
        assertTrue("summary 应包含温度符号", first.summary.contains("°"))

        // 第二次调用应命中 30 分钟缓存（fetchedAt 不变即为同一条缓存）
        val second = repository.getWeather(zzEast.first, zzEast.second)
        assertNotNull(second)
        assertEquals(first.fetchedAt, second!!.fetchedAt)
        assertEquals(first.weatherCode, second.weatherCode)
    }

    /**
     * 模拟京广高铁行进：郑州东 → 郑州航空港 → 许昌东 → 漯河西。
     * 验证"列车开到哪里算进入另一个城市"：
     * - 航空港仍属郑州（不触发切换）；
     * - 进入许昌触发 郑州→许昌；
     * - 进入漯河触发 许昌→漯河。
     */
    @Test(timeout = 120_000)
    fun cityTransition_alongJingguangRailway() = runBlocking {
        val tracker = CityTransitionTracker(repository)
        tracker.reset()

        val transitions = mutableListOf<CityTransitionTracker.CityTransition>()

        // 沿途位置点（真实车站附近坐标，间距均大于一个 0.1° 网格）
        val route = listOf(
            34.7594 to 113.7736, // 郑州东
            34.5200 to 113.8400, // 郑州航空港（新郑，仍属郑州市）
            34.0035 to 113.8520, // 许昌东
            33.5810 to 113.9840, // 漯河西
        )

        for ((lat, lon) in route) {
            tracker.onLocationUpdate(lat, lon)?.let { transitions.add(it) }
        }

        assertEquals("郑州", tracker.currentCity?.let { transitions.firstOrNull()?.fromCity ?: it })
        assertEquals(2, transitions.size)
        assertEquals("郑州", transitions[0].fromCity)
        assertEquals("许昌", transitions[0].toCity)
        assertEquals("许昌", transitions[1].fromCity)
        assertEquals("漯河", transitions[1].toCity)

        // 每次切换都应携带新城市的天气
        for (t in transitions) {
            assertTrue(t.weather.weatherCode in 0..58)
            assertEquals(t.toCity, t.weather.cityName)
        }
        assertEquals("漯河", tracker.currentCity)
        assertNotNull(tracker.currentWeather)
    }

    @Test
    fun geoGrid_sameStationAreaSharesOneCell() {
        // 郑州东站附近几百米内的两个坐标应落在同一网格（共享天气缓存）
        val a = GeoGrid.keyOf(34.7594, 113.7736)
        val b = GeoGrid.keyOf(34.7620, 113.7690)
        assertEquals(a, b)
    }
}
