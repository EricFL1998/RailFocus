package com.hsr.railfocus.data.repository

import com.hsr.railfocus.data.local.dataaccess.WeatherDataAccess
import com.hsr.railfocus.data.local.entity.WeatherCacheEntity
import com.hsr.railfocus.data.remote.weather.XiaomiWeatherApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WeatherRepositoryTest {

    private val api: XiaomiWeatherApi = mockk()
    private val dao: WeatherDataAccess = mockk(relaxed = true)
    private lateinit var repository: WeatherRepository

    private var now = 1_000_000_000L

    private val lat = 34.7466
    private val lon = 113.6254
    private val gridKey = "34.7,113.6"

    private val geoResult = XiaomiWeatherApi.GeoResult(
        locationKey = "weathercn:101180109",
        areaName = "中原",
        cityName = "郑州",
        provinceName = "河南",
    )

    private val currentResult = XiaomiWeatherApi.CurrentResult(
        weatherCode = 7,
        temperatureC = 21.0,
    )

    private fun cachedEntity(
        geoFetchedAt: Long = now,
        weatherFetchedAt: Long = now,
    ) = WeatherCacheEntity(
        gridKey = gridKey,
        locationKey = geoResult.locationKey,
        areaName = geoResult.areaName,
        cityName = geoResult.cityName,
        provinceName = geoResult.provinceName,
        weatherCode = 7,
        temperatureC = 21.0,
        geoFetchedAt = geoFetchedAt,
        weatherFetchedAt = weatherFetchedAt,
    )

    @Before
    fun setUp() {
        repository = WeatherRepository(api, dao)
        repository.nowProvider = { now }
    }

    @Test
    fun freshCache_isReturnedWithoutNetwork() = runTest {
        coEvery { dao.getByGrid(gridKey) } returns cachedEntity(
            weatherFetchedAt = now - 10 * 60 * 1000L, // 10 分钟前，仍新鲜
        )

        val result = repository.getWeather(lat, lon)

        assertNotNull(result)
        assertEquals("weathercn:101180109", result!!.locationKey)
        assertEquals(7, result.weatherCode)
        assertEquals(21.0, result.temperatureC, 0.001)
        coVerify(exactly = 0) { api.reverseGeo(any(), any()) }
        coVerify(exactly = 0) { api.fetchCurrent(any(), any(), any()) }
    }

    @Test
    fun staleCache_triggersWeatherRefresh_butReusesFreshGeo() = runTest {
        coEvery { dao.getByGrid(gridKey) } returns cachedEntity(
            geoFetchedAt = now - 24 * 60 * 60 * 1000L, // geo 1 天前，仍有效
            weatherFetchedAt = now - 60 * 60 * 1000L, // 天气 1 小时前，过期
        )
        coEvery { api.fetchCurrent(lat, lon, geoResult.locationKey) } returns currentResult

        val result = repository.getWeather(lat, lon)

        assertNotNull(result)
        assertEquals("郑州", result!!.cityName)
        // 归属地未过期，不应重新反查
        coVerify(exactly = 0) { api.reverseGeo(any(), any()) }
        coVerify(exactly = 1) { api.fetchCurrent(lat, lon, geoResult.locationKey) }
        coVerify(exactly = 1) { dao.upsert(any()) }
    }

    @Test
    fun expiredGeo_triggersReverseGeo() = runTest {
        coEvery { dao.getByGrid(gridKey) } returns cachedEntity(
            geoFetchedAt = now - 8L * 24 * 60 * 60 * 1000, // geo 8 天前，过期
            weatherFetchedAt = now - 60 * 60 * 1000L,
        )
        coEvery { api.reverseGeo(lat, lon) } returns geoResult
        coEvery { api.fetchCurrent(lat, lon, geoResult.locationKey) } returns currentResult

        val result = repository.getWeather(lat, lon)

        assertNotNull(result)
        coVerify(exactly = 1) { api.reverseGeo(lat, lon) }
    }

    @Test
    fun coldStart_successfulFetch_persistsEntity() = runTest {
        coEvery { dao.getByGrid(gridKey) } returns null
        coEvery { api.reverseGeo(lat, lon) } returns geoResult
        coEvery { api.fetchCurrent(lat, lon, geoResult.locationKey) } returns currentResult

        val result = repository.getWeather(lat, lon)

        assertNotNull(result)
        assertEquals("中原", result!!.areaName)
        assertEquals("郑州", result.cityName)
        coVerify(exactly = 1) {
            dao.upsert(match {
                it.gridKey == gridKey &&
                    it.locationKey == geoResult.locationKey &&
                    it.weatherCode == 7 &&
                    it.geoFetchedAt > 0
            })
        }
    }

    @Test
    fun networkFailure_withStaleCache_fallsBackToStaleCache() = runTest {
        coEvery { dao.getByGrid(gridKey) } returns cachedEntity(
            geoFetchedAt = now - 8L * 24 * 60 * 60 * 1000,
            weatherFetchedAt = now - 60 * 60 * 1000L,
        )
        coEvery { api.reverseGeo(lat, lon) } returns null // 弱网失败
        coEvery { api.fetchCurrent(lat, lon, geoResult.locationKey) } returns null

        val result = repository.getWeather(lat, lon)

        // 即使缓存过期，也回退旧数据而不是返回 null（高铁弱网场景）
        assertNotNull(result)
        assertEquals(7, result!!.weatherCode)
    }

    @Test
    fun networkFailure_withoutCache_returnsNull() = runTest {
        coEvery { dao.getByGrid(gridKey) } returns null
        coEvery { api.reverseGeo(lat, lon) } returns null
        coEvery { api.fetchCurrent(lat, lon, null) } returns null

        val result = repository.getWeather(lat, lon)

        assertNull(result)
    }

    @Test
    fun geoFailure_weatherStillFetchedWithEmptyKey() = runTest {
        coEvery { dao.getByGrid(gridKey) } returns null
        coEvery { api.reverseGeo(lat, lon) } returns null
        coEvery { api.fetchCurrent(lat, lon, null) } returns currentResult

        val result = repository.getWeather(lat, lon)

        // geo 失败不应阻断天气查询（服务端可按坐标匹配最近站点）
        assertNotNull(result)
        assertEquals("", result!!.locationKey)
        assertEquals(21.0, result.temperatureC, 0.001)
    }

    @Test
    fun prefetch_deduplicatesSameGridAndSkipsFreshCache() = runTest {
        // 两个坐标落在同一网格，一个落在相邻网格
        val p1 = 34.71 to 113.61
        val p2 = 34.74 to 113.64
        val p3 = 39.91 to 116.41 // 北京，另一个网格
        val grid1 = "34.7,113.6"
        val grid3 = "39.9,116.4"

        // grid1 缓存新鲜应跳过；grid3 无缓存应请求
        coEvery { dao.getByGrid(grid1) } returns cachedEntity(weatherFetchedAt = now)
        coEvery { dao.getByGrid(grid3) } returns null
        coEvery { api.reverseGeo(p3.first, p3.second) } returns geoResult
        coEvery { api.fetchCurrent(p3.first, p3.second, geoResult.locationKey) } returns currentResult

        repository.prefetch(listOf(p1, p2, p3))

        coVerify(exactly = 0) { api.fetchCurrent(p1.first, p1.second, any()) }
        coVerify(exactly = 0) { api.fetchCurrent(p2.first, p2.second, any()) }
        coVerify(exactly = 1) { api.fetchCurrent(p3.first, p3.second, geoResult.locationKey) }
    }

    @Test
    fun forceRefresh_ignoresWeatherTtl() = runTest {
        coEvery { dao.getByGrid(gridKey) } returns cachedEntity(
            weatherFetchedAt = now, // 缓存新鲜，但强制刷新
        )
        coEvery { api.fetchCurrent(lat, lon, geoResult.locationKey) } returns currentResult

        repository.getWeather(lat, lon, forceRefresh = true)

        coVerify(exactly = 1) { api.fetchCurrent(lat, lon, geoResult.locationKey) }
    }
}
