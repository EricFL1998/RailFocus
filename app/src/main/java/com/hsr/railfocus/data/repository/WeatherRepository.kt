package com.hsr.railfocus.data.repository

import com.hsr.railfocus.data.local.dataaccess.WeatherDataAccess
import com.hsr.railfocus.data.local.entity.WeatherCacheEntity
import com.hsr.railfocus.data.remote.weather.XiaomiWeatherApi
import com.hsr.railfocus.domain.model.WeatherInfo
import com.hsr.railfocus.util.GeoGrid
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * 天气仓库：为任意地点（车站 / 列车当前位置）提供当前天气。
 *
 * 缓存与请求策略：
 * - 以 0.1° 地理网格（[GeoGrid]）为缓存单元，同网格内多个车站共享一份数据；
 * - 归属地反查（geo）7 天内不重复请求；天气 30 分钟内不重复请求；
 * - 同一网格的并发请求通过 per-grid 互斥锁收敛为一次（锁内双重检查）；
 * - 网络失败时回退到旧缓存（即使已过期），保证高铁弱网环境下仍有数据可用。
 *
 * "地点边界"问题的答案：边界判定由小米 geo 反查的服务端行政区数据完成，
 * 客户端只按网格缓存其结果，不维护本地边界多边形。
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val api: XiaomiWeatherApi,
    private val weatherDataAccess: WeatherDataAccess,
    private val preferencesRepository: com.hsr.railfocus.data.preferences.UserPreferencesRepository? = null,
) {
    companion object {
        /** 天气缓存有效期：30 分钟 */
        const val WEATHER_TTL_MS: Long = 30 * 60 * 1000L

        /** 归属地缓存有效期：7 天（行政区划几乎不变） */
        const val GEO_TTL_MS: Long = 7L * 24 * 60 * 60 * 1000

        /** 后台批量预取的并发上限 */
        private const val PREFETCH_CONCURRENCY = 4
    }

    /** 每个网格一把锁，收敛同一网格的并发刷新 */
    private val gridLocks = ConcurrentHashMap<String, Mutex>()

    /** 当前时间（可注入以便测试） */
    internal var nowProvider: () -> Long = { System.currentTimeMillis() }

    /**
     * 获取指定坐标的当前天气。
     *
     * 缓存新鲜（30 分钟内）时直接返回缓存；否则后台刷新归属地与天气。
     * 刷新失败时回退到旧缓存；从未有过数据且请求失败时返回 null。
     *
     * @param forceRefresh 忽略天气缓存有效期强制刷新（归属地仍按 7 天有效期复用）
     */
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean = false,
    ): WeatherInfo? {
        if (preferencesRepository?.weatherDisplayEnabled?.first() == false) {
            return null
        }
        val gridKey = GeoGrid.keyOf(latitude, longitude)
        val now = nowProvider()

        val cached = weatherDataAccess.getByGrid(gridKey)
        if (!forceRefresh && cached != null && now - cached.weatherFetchedAt < WEATHER_TTL_MS) {
            return cached.toDomain()
        }

        val mutex = gridLocks.getOrPut(gridKey) { Mutex() }
        return mutex.withLock {
            // 双重检查：等待锁期间可能已有其他协程刷新过
            val recheck = weatherDataAccess.getByGrid(gridKey)
            val recheckNow = nowProvider()
            if (!forceRefresh && recheck != null &&
                recheckNow - recheck.weatherFetchedAt < WEATHER_TTL_MS
            ) {
                return@withLock recheck.toDomain()
            }
            refreshLocked(gridKey, latitude, longitude, recheck, recheckNow)
        }
    }

    /**
     * 后台批量预取多个地点的天气（供目的地列表、旅程沿途车站使用）。
     *
     * 按网格去重、跳过缓存仍新鲜的网格，最多 [PREFETCH_CONCURRENCY] 路并发；
     * 单个地点失败不影响其他地点，也不抛出异常。
     */
    suspend fun prefetch(points: List<Pair<Double, Double>>) {
        if (preferencesRepository?.weatherDisplayEnabled?.first() == false) {
            return
        }
        val now = nowProvider()
        val targets = points
            .distinctBy { GeoGrid.keyOf(it.first, it.second) }
            .filter { (lat, lon) ->
                val cached = weatherDataAccess.getByGrid(GeoGrid.keyOf(lat, lon))
                cached == null || now - cached.weatherFetchedAt >= WEATHER_TTL_MS
            }
        val semaphore = Semaphore(PREFETCH_CONCURRENCY)
        supervisorScope {
            targets.map { (lat, lon) ->
                launch {
                    semaphore.withPermit {
                        runCatching { getWeather(lat, lon) }
                    }
                }
            }.forEach { it.join() }
        }
    }

    /** 锁内刷新：先按需反查归属地，再拉取天气，写缓存 */
    private suspend fun refreshLocked(
        gridKey: String,
        latitude: Double,
        longitude: Double,
        cached: WeatherCacheEntity?,
        now: Long,
    ): WeatherInfo? {
        var locationKey = cached?.locationKey.orEmpty()
        var areaName = cached?.areaName.orEmpty()
        var cityName = cached?.cityName.orEmpty()
        var provinceName = cached?.provinceName.orEmpty()
        var geoFetchedAt = cached?.geoFetchedAt ?: 0L

        // 1. 归属地：从未成功反查或已过期时才请求
        if (now - geoFetchedAt >= GEO_TTL_MS) {
            val geo = api.reverseGeo(latitude, longitude)
            if (geo != null) {
                locationKey = geo.locationKey
                areaName = geo.areaName
                cityName = geo.cityName
                provinceName = geo.provinceName
                geoFetchedAt = now
            }
            // geo 失败但有旧缓存：沿用旧归属地继续查天气
        }

        // 2. 天气：按坐标 + 归属站点查询（locationKey 为空时服务端按最近站点返回）
        val current = api.fetchCurrent(latitude, longitude, locationKey.ifBlank { null })
        if (current != null) {
            val entity = WeatherCacheEntity(
                gridKey = gridKey,
                locationKey = locationKey,
                areaName = areaName,
                cityName = cityName,
                provinceName = provinceName,
                weatherCode = current.weatherCode,
                temperatureC = current.temperatureC,
                geoFetchedAt = geoFetchedAt,
                weatherFetchedAt = nowProvider(),
            )
            weatherDataAccess.upsert(entity)
            return entity.toDomain()
        }

        // 3. 天气也失败：回退旧缓存（可能已过期，但好过没有）
        return cached?.toDomain()
    }
}

private fun WeatherCacheEntity.toDomain(): WeatherInfo = WeatherInfo(
    locationKey = locationKey,
    areaName = areaName,
    cityName = cityName,
    weatherCode = weatherCode,
    temperatureC = temperatureC,
    fetchedAt = weatherFetchedAt,
)
